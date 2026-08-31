# -*- coding: utf-8 -*-
"""
场景 2: 社交关系域高并发测试
包含：高并发关注风暴、高并发取关风暴、关注列表游标分页、粉丝列表游标分页
"""
import random
from concurrent.futures import ThreadPoolExecutor
from ..config import DEFAULT_CONCURRENCY, DEFAULT_ROUNDS, DEFAULT_TARGET_USER_ID, SEED_USERS, TestContext
from ..stats import ScenarioStats


def run(client, concurrency: int = DEFAULT_CONCURRENCY, rounds: int = DEFAULT_ROUNDS) -> list:
    results = []
    # 动态选取已注册的目标大V用户
    target_user_id = TestContext.user_ids[0] if TestContext.user_ids else DEFAULT_TARGET_USER_ID

    # 1. 高并发关注风暴
    stats_follow = ScenarioStats("高并发关注风暴", "社交关系域", "多并发用户同时关注目标大V用户")
    stats_follow.start()

    def do_follow(idx):
        user_id = 9000000 + idx
        payload = {"followUserId": target_user_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "user", "/relation/follow", json_data=payload, user_id=user_id
        )
        stats_follow.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_follow, range(rounds)))
    stats_follow.finish()
    results.append(stats_follow)

    # 2. 关注列表分页高并发拉取
    stats_following_list = ScenarioStats("关注列表分页查询", "社交关系域", "关注列表游标/分页高频拉取")
    stats_following_list.start()

    def do_following_list(idx):
        user_id = TestContext.user_ids[idx % len(TestContext.user_ids)] if TestContext.user_ids else 1001
        payload = {"userId": user_id, "pageNo": 1, "pageSize": 20}
        lat, status, ok, code, data, err = client.request(
            "POST", "user", "/relation/following/list", json_data=payload, user_id=user_id
        )
        stats_following_list.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_following_list, range(rounds)))
    stats_following_list.finish()
    results.append(stats_following_list)

    # 3. 粉丝列表分页高并发拉取
    stats_fans_list = ScenarioStats("粉丝列表分页查询", "社交关系域", "大V粉丝列表高并发拉取 (Redis ZSet/DB)")
    stats_fans_list.start()

    def do_fans_list(idx):
        payload = {"userId": target_user_id, "pageNo": 1, "pageSize": 20}
        lat, status, ok, code, data, err = client.request(
            "POST", "user", "/relation/fans/list", json_data=payload, user_id=target_user_id
        )
        stats_fans_list.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_fans_list, range(rounds)))
    stats_fans_list.finish()
    results.append(stats_fans_list)

    # 4. 高并发取关风暴
    stats_unfollow = ScenarioStats("高并发取关风暴", "社交关系域", "多并发用户同时取消关注")
    stats_unfollow.start()

    def do_unfollow(idx):
        user_id = 9000000 + idx
        payload = {"unfollowUserId": target_user_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "user", "/relation/unfollow", json_data=payload, user_id=user_id
        )
        stats_unfollow.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_unfollow, range(rounds)))
    stats_unfollow.finish()
    results.append(stats_unfollow)

    return results
