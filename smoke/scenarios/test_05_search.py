# -*- coding: utf-8 -*-
"""
场景 5: 全文检索与搜索域高并发测试
包含：笔记关键词全文检索、笔记多维度排序筛选检索、用户关键词检索
"""
import random
from concurrent.futures import ThreadPoolExecutor
from ..config import DEFAULT_CONCURRENCY, DEFAULT_ROUNDS, SEED_USERS
from ..stats import ScenarioStats

KEYWORDS = ["Java", "Spring", "Redis", "MySQL", "RocketMQ", "高并发", "架构", "设计模式", "Smoke", "测试"]


def run(client, concurrency: int = DEFAULT_CONCURRENCY, rounds: int = DEFAULT_ROUNDS) -> list:
    results = []

    # 1. 笔记全文关键词检索
    stats_note_search = ScenarioStats("笔记关键词全文检索", "搜索检索域", "笔记全文多关键词并发检索 (Elasticsearch/Lucene)")
    stats_note_search.start()

    def do_note_search(idx):
        kw = KEYWORDS[idx % len(KEYWORDS)]
        payload = {"keyword": kw, "pageNo": 1, "pageSize": 10, "sortType": 0}
        lat, status, ok, code, data, err = client.request(
            "POST", "search", "/search/note", json_data=payload, user_id=SEED_USERS[0]["user_id"]
        )
        stats_note_search.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_note_search, range(rounds)))
    stats_note_search.finish()
    results.append(stats_note_search)

    # 2. 用户关键词/昵称并发检索
    stats_user_search = ScenarioStats("用户关键词检索", "搜索检索域", "用户昵称/ID关键词并发检索")
    stats_user_search.start()

    def do_user_search(idx):
        kw = f"SmokeUser{idx % 5}"
        payload = {"keyword": kw, "pageNo": 1, "pageSize": 10}
        lat, status, ok, code, data, err = client.request(
            "POST", "search", "/search/user", json_data=payload, user_id=SEED_USERS[0]["user_id"]
        )
        stats_user_search.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_user_search, range(rounds)))
    stats_user_search.finish()
    results.append(stats_user_search)

    return results
