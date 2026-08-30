# -*- coding: utf-8 -*-
"""
场景 6: 计数服务域高并发测试
包含：笔记交互计数(点赞/收藏/评论/浏览)高频聚合拉取、用户个人计数(关注/粉丝/获赞)高频拉取
"""
import random
from concurrent.futures import ThreadPoolExecutor
from ..config import DEFAULT_CONCURRENCY, DEFAULT_ROUNDS, DEFAULT_HOT_NOTE_ID, DEFAULT_TARGET_USER_ID, TestContext
from ..stats import ScenarioStats


def run(client, concurrency: int = DEFAULT_CONCURRENCY, rounds: int = DEFAULT_ROUNDS) -> list:
    results = []
    target_note_id = TestContext.hot_note_id if TestContext.hot_note_id else DEFAULT_HOT_NOTE_ID
    target_user_id = TestContext.user_ids[0] if TestContext.user_ids else DEFAULT_TARGET_USER_ID

    # 1. 笔记计数高频聚合拉取
    stats_note_count = ScenarioStats("笔记多维计数读取", "计数服务域", "笔记多维计数(点赞/收藏/评论)高频并发读取")
    stats_note_count.start()

    def do_note_count(_):
        payload = {"noteIds": [target_note_id]}
        lat, status, ok, code, data, err = client.request(
            "POST", "count", "/count/notes/data", json_data=payload
        )
        stats_note_count.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_note_count, range(rounds * 2)))
    stats_note_count.finish()
    results.append(stats_note_count)

    # 2. 用户计数高频拉取
    stats_user_count = ScenarioStats("用户多维计数读取", "计数服务域", "用户个人计数(关注/粉丝/获赞)高频并发读取")
    stats_user_count.start()

    def do_user_count(_):
        payload = {"userId": target_user_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "count", "/count/user/data", json_data=payload
        )
        stats_user_count.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_user_count, range(rounds * 2)))
    stats_user_count.finish()
    results.append(stats_user_count)

    return results
