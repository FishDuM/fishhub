# -*- coding: utf-8 -*-
"""
FishHub 压力测试框架 - 全局配置中心
支持通过环境变量动态覆盖，支持网关代理模式与微服务直连模式。
"""
import os
import socket

# ==================== 服务基础地址配置 ====================
# 默认优先通过网关 (8000) 统一路由压测，真实还原生产链路
GATEWAY_URL = os.getenv("FISHHUB_GATEWAY_URL", "http://127.0.0.1:8000")

# 各微服务独立直连端口 (用于绕过网关直测单域极限性能或网关未启动时降级)
SERVICES_DIRECT = {
    "user": os.getenv("FISHHUB_USER_URL", "http://127.0.0.1:8001"),
    "note": os.getenv("FISHHUB_NOTE_URL", "http://127.0.0.1:8002"),
    "count": os.getenv("FISHHUB_COUNT_URL", "http://127.0.0.1:8003"),
    "search": os.getenv("FISHHUB_SEARCH_URL", "http://127.0.0.1:8004"),
    "comment": os.getenv("FISHHUB_COMMENT_URL", "http://127.0.0.1:8005"),
}

# ==================== 压测默认参数 ====================
DEFAULT_CONCURRENCY = int(os.getenv("STRESS_CONCURRENCY", "50"))   # 默认并发线程数
DEFAULT_ROUNDS = int(os.getenv("STRESS_ROUNDS", "200"))            # 默认单场景压测请求轮次
REQUEST_TIMEOUT = float(os.getenv("STRESS_TIMEOUT", "10.0"))       # 单次请求超时时间 (秒)
OUTPUT_REPORT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reports")

# 默认本地数据库与 Redis 连接凭证 (用于一键清理脏数据)
DEFAULT_MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "3057433102")
DEFAULT_REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "3057433102")

# ==================== 预置基准测试数据 ====================
# 默认管理员/压测用户
SEED_USERS = [
    {"phone": "13811110001", "password": "SmokeUser1_pwd", "fishhub_id": "smoke001", "user_id": 2101},
    {"phone": "13811110002", "password": "SmokeUser2_pwd", "fishhub_id": "smoke002", "user_id": 2102},
]

# 预置基准笔记 ID (用于热点读取和并发互动压测)
DEFAULT_HOT_NOTE_ID = int(os.getenv("HOT_NOTE_ID", "2090769972610465829"))
DEFAULT_TARGET_USER_ID = int(os.getenv("TARGET_USER_ID", "2101"))


# ==================== 动态压测上下文数据 ====================
class TestContext:
    """动态维护压测种子数据，保证即便数据库为空也能自动预热自愈"""
    user_ids = []
    hot_note_id = None
    sample_comment_id = None


def check_port_alive(host: str = "127.0.0.1", port: int = 8000, timeout: float = 0.5) -> bool:
    """检查指定端口是否存活"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(timeout)
        s.connect((host, port))
        s.close()
        return True
    except Exception:
        return False


def detect_environment() -> dict:
    """检测当前后端服务运行状态"""
    status = {
        "gateway": check_port_alive("127.0.0.1", 8000),
        "user": check_port_alive("127.0.0.1", 8001),
        "note": check_port_alive("127.0.0.1", 8002),
        "count": check_port_alive("127.0.0.1", 8003),
        "search": check_port_alive("127.0.0.1", 8004),
        "comment": check_port_alive("127.0.0.1", 8005),
    }
    return status
