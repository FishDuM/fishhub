# -*- coding: utf-8 -*-
"""
场景 1: 用户认证与用户域高并发测试
包含：图形验证码突发请求、批量并发注册、免密/密码登录、用户资料高并发读取、批量用户查询
"""
import random
import time
from concurrent.futures import ThreadPoolExecutor
from ..config import DEFAULT_CONCURRENCY, DEFAULT_ROUNDS, SEED_USERS, TestContext
from ..stats import ScenarioStats


def run(client, concurrency: int = DEFAULT_CONCURRENCY, rounds: int = DEFAULT_ROUNDS) -> list:
    results = []

    # 1. 图形验证码高并发获取
    stats_captcha = ScenarioStats("验证码并发获取", "用户认证域", "高并发获取图形验证码")
    stats_captcha.start()

    def do_captcha(_):
        lat, status, ok, code, data, err = client.request("GET", "user", "/captcha")
        stats_captcha.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_captcha, range(rounds)))
    stats_captcha.finish()
    results.append(stats_captcha)

    # 2. 高并发用户解析与注册鉴权 (动态自愈生成可用用户池)
    stats_login = ScenarioStats("用户并发登录", "用户认证域", "高并发用户注册与解析 (构建高并发活跃用户池)")
    stats_login.start()

    dynamic_uids = []

    def do_login(idx):
        phone = f"138{idx % 100000000:08d}"
        payload = {"phone": phone}
        lat, status, ok, code, data, err = client.request("POST", "user", "/user/resolve-loginable", json_data=payload)
        if ok and isinstance(data, dict) and data.get("data"):
            uid_data = data.get("data", {}).get("userId")
            if uid_data:
                dynamic_uids.append(int(uid_data))
        stats_login.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_login, range(rounds)))
    stats_login.finish()
    results.append(stats_login)

    # 同步更新动态用户池
    if dynamic_uids:
        TestContext.user_ids = list(set(dynamic_uids))
    elif not TestContext.user_ids:
        TestContext.user_ids = [1001, 1002]

    # 3. 高并发个人主页/资料查询 (多级缓存热读)
    stats_profile = ScenarioStats("用户主页资料查询", "用户业务域", "用户 Profile 高并发读取 (Redis/Caffeine)")
    stats_profile.start()

    def do_profile(idx):
        uid = TestContext.user_ids[idx % len(TestContext.user_ids)]
        payload = {"userId": uid}
        lat, status, ok, code, data, err = client.request(
            "POST", "user", "/user/profile", json_data=payload, user_id=uid
        )
        stats_profile.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_profile, range(rounds)))
    stats_profile.finish()
    results.append(stats_profile)

    # 4. 批量用户资料并发查询
    stats_batch = ScenarioStats("批量用户资料查询", "用户业务域", "批量用户信息高并发拉取")
    stats_batch.start()

    sample_uids = TestContext.user_ids[:10] if len(TestContext.user_ids) >= 10 else TestContext.user_ids

    def do_batch(_):
        payload = {"ids": sample_uids}
        lat, status, ok, code, data, err = client.request(
            "POST", "user", "/user/findByIds", json_data=payload, user_id=sample_uids[0] if sample_uids else 1001
        )
        stats_batch.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_batch, range(rounds)))
    stats_batch.finish()
    results.append(stats_batch)

    return results
