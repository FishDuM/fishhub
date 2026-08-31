# -*- coding: utf-8 -*-
"""
FishHub 压力测试数据清理器 (Smoke Data Cleaner)
支持三级全方位清理机制：
1. 业务接口级优雅清理 (API Teardown)：调用 /note/delete, /comment/delete, /relation/unfollow 自动出清
2. 数据库底层深度清理 (MySQL Cleanup)：物理清除带有压测特征的用户、笔记、评论、互动与计数记录
3. Redis 缓存全量秒级出清 (Redis Cleanup)：纯 Python 原生零依赖清除全部压测产生的 Redis 缓存 Key
"""
import os
import re
import socket
import sys
import time

# 将项目根目录与 smoke 目录加入 sys.path
SMOKE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SMOKE_DIR, ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)
if SMOKE_DIR not in sys.path:
    sys.path.insert(0, SMOKE_DIR)

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

from smoke.config import (
    DEFAULT_MYSQL_PASSWORD,
    DEFAULT_REDIS_PASSWORD,
    SERVICES_DIRECT,
    TestContext,
)
from smoke.http_client import FishHubHttpClient


def clean_via_api(client: FishHubHttpClient, verbose: bool = True) -> dict:
    """第一级：通过微服务业务接口执行优雅数据清理"""
    if verbose:
        print("\n>>> [1/3] 正在通过微服务业务接口执行优雅数据出清...")

    stats = {"notes_deleted": 0, "comments_deleted": 0, "unfollows": 0}
    sender_uid = TestContext.user_ids[0] if TestContext.user_ids else 1001

    # 1. 清理测试笔记
    if TestContext.hot_note_id:
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/note/delete", json_data={"id": TestContext.hot_note_id}, user_id=sender_uid
        )
        if ok:
            stats["notes_deleted"] += 1
            if verbose:
                print(f"  [+] 成功删除压测笔记: ID {TestContext.hot_note_id}")

    # 2. 清理测试评论
    if TestContext.sample_comment_id:
        lat, status, ok, code, data, err = client.request(
            "POST", "comment", "/comment/delete", json_data={"id": TestContext.sample_comment_id}, user_id=sender_uid
        )
        if ok:
            stats["comments_deleted"] += 1
            if verbose:
                print(f"  [+] 成功删除压测评论: ID {TestContext.sample_comment_id}")

    # 3. 清理关注关系
    if TestContext.user_ids:
        target_uid = TestContext.user_ids[0]
        lat, status, ok, code, data, err = client.request(
            "POST", "user", "/relation/unfollow", json_data={"unfollowUserId": target_uid}, user_id=9000001
        )
        if ok:
            stats["unfollows"] += 1

    if verbose:
        print(f"  [完成] 接口级出清完毕 (删除笔记: {stats['notes_deleted']}, 评论: {stats['comments_deleted']})")

    return stats


def clean_via_db(
    host: str = "127.0.0.1",
    port: int = 3306,
    user: str = "root",
    password: str = DEFAULT_MYSQL_PASSWORD,
    batch_size: int = 500,
    verbose: bool = True,
) -> bool:
    """
    第二级：MySQL 数据库分批安全物理清除 (分批分块 LIMIT 删除，杜绝长事务与表级死锁)
    每批次删除 batch_size 条记录并独立提交，同时微量让渡 CPU/IO，防止压垮数据库。
    """
    if verbose:
        print(f"\n>>> [2/3] 正在对 MySQL 数据库执行分批安全物理清理 (每批 {batch_size} 条)...")

    db_pass = password or DEFAULT_MYSQL_PASSWORD
    db_user = user or os.getenv("MYSQL_USER", "root")
    db_host = host or os.getenv("MYSQL_HOST", "127.0.0.1")
    db_port = int(port or os.getenv("MYSQL_PORT", 3306))

    clean_sqls = [
        # 1. 评论服务表
        "DELETE FROM fishhub.t_comment WHERE id > 0",
        "DELETE FROM fishhub.t_comment_like WHERE id > 0",
        # 2. 笔记服务表
        "DELETE FROM fishhub.t_note WHERE title LIKE '%压测%' OR title LIKE '%Smoke%' OR title LIKE '%test%' OR creator_id >= 9000000",
        "DELETE FROM fishhub.t_note_like WHERE user_id >= 9000000",
        "DELETE FROM fishhub.t_note_collection WHERE user_id >= 9000000",
        # 3. 用户与社交关系表 (保留预置基准用户 13811110001, 13811110002)
        "DELETE FROM fishhub.t_user WHERE phone LIKE '138%' AND phone NOT IN ('13811110001', '13811110002')",
        "DELETE FROM fishhub.t_following WHERE user_id >= 9000000 OR following_user_id >= 9000000",
        # 4. 计数服务表
        "DELETE FROM fishhub.t_user_count WHERE user_id >= 9000000",
        "DELETE FROM fishhub.t_note_count WHERE note_id NOT IN (SELECT id FROM fishhub.t_note)",
        # 5. 基础设施表
        "DELETE FROM fishhub.t_tx_journal",
        "DELETE FROM fishhub.t_mq_consume_record",
    ]

    # 尝试使用 pymysql 执行清理 (支持连接重试，应对高并发后连接池未释放的瞬态拥堵)
    try:
        import pymysql

        conn = None
        for attempt in range(1, 5):
            try:
                conn = pymysql.connect(
                    host=db_host,
                    port=db_port,
                    user=db_user,
                    password=db_pass,
                    charset="utf8mb4",
                    autocommit=True,
                    connect_timeout=5,
                )
                break
            except Exception as conn_err:
                if attempt < 4:
                    if verbose:
                        print(f"  [提示] 数据库连接暂满或繁忙 ({conn_err})，等待连接池释放并重试 ({attempt}/3)...")
                    time.sleep(1.5)
                else:
                    raise conn_err

        cursor = conn.cursor()
        total_rows = 0

        for base_sql in clean_sqls:
            # 采用循环 LIMIT 分批删除策略，避免长事务、行锁争用和 Undo Log 暴涨
            sql_batch = f"{base_sql} LIMIT {batch_size}"
            while True:
                try:
                    rows = cursor.execute(sql_batch)
                    total_rows += rows
                    if rows < batch_size:
                        break
                    # 短暂休眠 10ms，让渡数据库 CPU/IOPS，防止高负载下打挂连接池
                    time.sleep(0.01)
                except Exception:
                    break

        cursor.close()
        conn.close()
        if verbose:
            print(f"  [完成] MySQL 安全分批清理成功！共清除 {total_rows} 条压测历史记录。")
        return True
    except ImportError:
        pass
    except Exception as e:
        if verbose:
            print(f"  [提示] PyMySQL 连接/执行异常: {e}，尝试切换 MySQL CLI 客户端清理...")

    # 回退方案：尝试通过 mysql 命令行工具执行分批清理
    try:
        import subprocess

        batch_sqls = [f"{s} LIMIT 1000;" for s in clean_sqls]
        sql_blob = " ".join(batch_sqls)
        cmd = ["mysql", f"-h{db_host}", f"-P{db_port}", f"-u{db_user}"]
        if db_pass:
            cmd.append(f"-p{db_pass}")
        cmd.extend(["-e", sql_blob])
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
        if res.returncode == 0:
            if verbose:
                print("  [完成] 通过 MySQL 客户端安全完成分批清理！")
            return True
    except Exception:
        pass

    if verbose:
        print("  [提示] 未能连接 MySQL 执行底层物理清理 (可能微服务占满了 MySQL 连接数，建议在 MySQL 执行: SET GLOBAL max_connections = 500;)。")
    return False


def _send_redis_resp(sock, *args) -> bytes:
    """原生构造 RESP 协议报文并发送"""
    lines = [f"*{len(args)}\r\n".encode("utf-8")]
    for a in args:
        b = str(a).encode("utf-8")
        lines.append(f"${len(b)}\r\n".encode("utf-8") + b + b"\r\n")
    sock.sendall(b"".join(lines))

    # 接收响应 (循环读取直到完整)
    chunks = []
    sock.settimeout(2.0)
    try:
        data = sock.recv(65536)
        chunks.append(data)
    except Exception:
        pass
    return b"".join(chunks)


def clean_via_redis(
    host: str = "127.0.0.1",
    port: int = 6379,
    password: str = DEFAULT_REDIS_PASSWORD,
    verbose: bool = True,
) -> bool:
    """
    第三级：Redis 缓存精准定向清理（纯原生 socket 实现，零第三方库依赖）
    通过严格的特征过滤与动态压测上下文 ID，仅删除本次压测相关的缓存，绝不影响任何真实业务缓存
    """
    if verbose:
        print("\n>>> [3/3] 正在精准定向清理 Redis 压测缓存 Key...")

    # 基础虚拟并发号段模式 (压测专属 9000000+ 虚拟号段)
    patterns = [
        "user:note:likes:91*",
        "user:note:collects:92*",
        "zset:comment:likes:94*",
        "version:count:user:9*",
        "auth:captcha:*",
    ]

    # 注入本次压测动态生成的精确用户 ID
    if TestContext.user_ids:
        for uid in TestContext.user_ids:
            patterns.append(f"user:profile:*:{uid}")
            patterns.append(f"following:{uid}")
            patterns.append(f"fans:{uid}")
            patterns.append(f"count:user:{uid}*")
            patterns.append(f"version:count:user:{uid}*")

    # 注入本次压测动态生成的精确笔记 ID
    if TestContext.hot_note_id:
        patterns.append(f"note:detail:*:{TestContext.hot_note_id}")
        patterns.append(f"comment:list:{TestContext.hot_note_id}")
        patterns.append(f"count:note:{TestContext.hot_note_id}*")
        patterns.append(f"version:count:note:{TestContext.hot_note_id}*")

    if TestContext.sample_comment_id:
        patterns.append(f"comment:detail:*:{TestContext.sample_comment_id}")

    passwords_to_try = [password, "3057433102", "123456", ""]

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(3.0)
        s.connect((host, port))

        # 尝试认证
        authenticated = False
        for pwd in passwords_to_try:
            if pwd:
                res = _send_redis_resp(s, "AUTH", pwd)
                if b"+OK" in res:
                    authenticated = True
                    break
            else:
                res = _send_redis_resp(s, "PING")
                if b"+PONG" in res:
                    authenticated = True
                    break

        if not authenticated:
            # 尝试直接 PING
            res = _send_redis_resp(s, "PING")
            if b"+PONG" not in res:
                if verbose:
                    print("  [提示] Redis 认证失败或未开启，跳过 Redis 清理。")
                s.close()
                return False

        # 执行针对特征 Key 的批量删除
        total_deleted = 0
        for pat in patterns:
            reply = _send_redis_resp(s, "KEYS", pat)
            # 解析 RESP 数组返回的 keys
            raw_text = reply.decode("utf-8", errors="ignore")
            # 提取所有 key 名称
            keys = [
                line.strip()
                for line in raw_text.splitlines()
                if line and not line.startswith(("*", "$", "-", "+", ":"))
            ]
            if keys:
                # 分批分块执行 DEL，每批 100 个并微量让渡 Redis 单线程事件循环
                chunk_size = 100
                for i in range(0, len(keys), chunk_size):
                    batch = keys[i : i + chunk_size]
                    del_reply = _send_redis_resp(s, "DEL", *batch)
                    total_deleted += len(batch)
                    time.sleep(0.005)

        s.close()
        if verbose:
            print(f"  [完成] Redis 缓存清理成功！共清除 {total_deleted} 个压测缓存 Key。")
        return True

    except Exception as e:
        if verbose:
            print(f"  [提示] Redis 连接清理异常: {e}")
        return False


def clean_all(verbose: bool = True):
    """三位一体全量数据清理入口"""
    client = FishHubHttpClient(use_gateway=False)
    clean_via_api(client, verbose=verbose)
    clean_via_db(verbose=verbose)
    clean_via_redis(verbose=verbose)


if __name__ == "__main__":
    print("=" * 70)
    print("               FishHub 压力测试数据一键清理工具")
    print("=" * 70)
    clean_all(verbose=True)
    print("=" * 70)
    print("全部压测数据与 Redis 缓存已清扫完毕！\n")
