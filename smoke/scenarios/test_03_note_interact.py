# -*- coding: utf-8 -*-
"""
场景 3: 笔记与内容互动域高并发测试
包含：高并发发布笔记、热点笔记详情高频读取(多级缓存抗压)、发现流/频道流刷新、高并发点赞与取消点赞、高并发收藏与取消收藏
"""
import random
import time
from concurrent.futures import ThreadPoolExecutor
from ..config import DEFAULT_CONCURRENCY, DEFAULT_ROUNDS, DEFAULT_HOT_NOTE_ID, SEED_USERS, TestContext
from ..stats import ScenarioStats


def run(client, concurrency: int = DEFAULT_CONCURRENCY, rounds: int = DEFAULT_ROUNDS) -> list:
    results = []
    sender_uid = TestContext.user_ids[0] if TestContext.user_ids else 1001

    # 1. 批量高并发发布图文笔记
    stats_publish = ScenarioStats("批量并发发布笔记", "笔记内容域", "多用户并发发布图文笔记")
    stats_publish.start()

    def do_publish(idx):
        user_id = TestContext.user_ids[idx % len(TestContext.user_ids)] if TestContext.user_ids else 1001
        payload = {
            "title": f"压测笔记_{idx}_{int(time.time()*1000)}",
            "content": f"这是自动化高并发压力测试自动发布的笔记正文 #{idx}。",
            "type": 0,
            "imgUris": ["https://img.fishhub.hk/smoke/test_cover.jpg"],
            "channelId": 3,
        }
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/note/publish", json_data=payload, user_id=user_id
        )
        stats_publish.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=min(concurrency, 30)) as ex:
        list(ex.map(do_publish, range(rounds // 2)))
    stats_publish.finish()
    results.append(stats_publish)

    # 2. 发现页与频道列表信息流高频拉取 (同时提取最新生成的真实 noteId)
    stats_feed = ScenarioStats("发现流与频道信息流", "笔记内容域", "发现流与频道信息流并发拉取")
    stats_feed.start()

    discovered_note_ids = []

    def do_feed(idx):
        payload = {"channelId": 3, "cursor": 0}
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/discover/note/list", json_data=payload, user_id=sender_uid
        )
        if ok and isinstance(data, dict) and data.get("data"):
            items = data.get("data")
            if items and isinstance(items, list) and len(items) > 0:
                first_nid = items[0].get("noteId") or items[0].get("id")
                if first_nid:
                    discovered_note_ids.append(int(first_nid))
        stats_feed.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_feed, range(rounds)))
    stats_feed.finish()
    results.append(stats_feed)

    # 动态确定目标笔记 ID
    if discovered_note_ids:
        TestContext.hot_note_id = discovered_note_ids[0]
    elif not TestContext.hot_note_id:
        TestContext.hot_note_id = DEFAULT_HOT_NOTE_ID

    target_note_id = TestContext.hot_note_id

    # 3. 热点笔记详情极高并发读取 (压测 Caffeine + Redis + DB 多级缓存与单飞防击穿)
    stats_detail = ScenarioStats("热点笔记详情读取", "笔记内容域", "热点笔记详情极高并发读取 (验证多级缓存抗压)")
    stats_detail.start()

    def do_detail(_):
        payload = {"id": target_note_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/note/detail", json_data=payload, user_id=sender_uid
        )
        stats_detail.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_detail, range(rounds * 2)))
    stats_detail.finish()
    results.append(stats_detail)

    # 4. 高并发笔记点赞浪涌 (压测 Redis HLL/Lua 缓存 + RocketMQ 事务与计数削峰)
    stats_like = ScenarioStats("高并发笔记点赞", "笔记互动域", "多并发用户同时对热点笔记点赞")
    stats_like.start()

    def do_like(idx):
        user_id = 9100000 + idx
        payload = {"id": target_note_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/note/like", json_data=payload, user_id=user_id
        )
        stats_like.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_like, range(rounds)))
    stats_like.finish()
    results.append(stats_like)

    # 5. 高并发笔记取消点赞
    stats_unlike = ScenarioStats("高并发取消点赞", "笔记互动域", "多并发用户同时对笔记取消点赞")
    stats_unlike.start()

    def do_unlike(idx):
        user_id = 9100000 + idx
        payload = {"id": target_note_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/note/unlike", json_data=payload, user_id=user_id
        )
        stats_unlike.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_unlike, range(rounds)))
    stats_unlike.finish()
    results.append(stats_unlike)

    # 6. 高并发笔记收藏
    stats_collect = ScenarioStats("高并发笔记收藏", "笔记互动域", "多并发用户同时对笔记进行收藏")
    stats_collect.start()

    def do_collect(idx):
        user_id = 9200000 + idx
        payload = {"id": target_note_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/note/collect", json_data=payload, user_id=user_id
        )
        stats_collect.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_collect, range(rounds)))
    stats_collect.finish()
    results.append(stats_collect)

    # 7. 高并发笔记取消收藏
    stats_uncollect = ScenarioStats("高并发取消收藏", "笔记互动域", "多并发用户同时取消笔记收藏")
    stats_uncollect.start()

    def do_uncollect(idx):
        user_id = 9200000 + idx
        payload = {"id": target_note_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "note", "/note/uncollect", json_data=payload, user_id=user_id
        )
        stats_uncollect.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_uncollect, range(rounds)))
    stats_uncollect.finish()
    results.append(stats_uncollect)

    return results
