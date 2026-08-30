# -*- coding: utf-8 -*-
"""
场景 4: 评论与热度域高并发测试
包含：高并发一级评论发布、二级子评论回复、评论列表热度排序分页拉取、子评论展开拉取、评论点赞与取消点赞
"""
import random
import time
from concurrent.futures import ThreadPoolExecutor
from ..config import DEFAULT_CONCURRENCY, DEFAULT_ROUNDS, DEFAULT_HOT_NOTE_ID, SEED_USERS, TestContext
from ..stats import ScenarioStats


def run(client, concurrency: int = DEFAULT_CONCURRENCY, rounds: int = DEFAULT_ROUNDS) -> list:
    results = []
    sender_uid = TestContext.user_ids[0] if TestContext.user_ids else (SEED_USERS[0]["user_id"] if SEED_USERS else 2101)

    # 动态预热自愈：若尚未存在目标笔记，先从发现流获取或快速发一篇基准测试笔记
    if not TestContext.hot_note_id:
        _, _, ok_feed, _, feed_data, _ = client.request(
            "POST", "note", "/discover/note/list", json_data={"channelId": 3, "cursor": 0}, user_id=sender_uid
        )
        if ok_feed and isinstance(feed_data, dict) and feed_data.get("data"):
            items = feed_data.get("data")
            if isinstance(items, list) and len(items) > 0 and (items[0].get("noteId") or items[0].get("id")):
                TestContext.hot_note_id = int(items[0].get("noteId") or items[0].get("id"))

        if not TestContext.hot_note_id:
            init_payload = {
                "title": f"压测基准笔记_{int(time.time()*1000)}",
                "content": "用于评论域独立高并发压力测试自动预热的基准笔记内容",
                "type": 0,
                "imgUris": ["https://img.fishhub.hk/smoke/test_cover.jpg"],
                "channelId": 3,
            }
            client.request("POST", "note", "/note/publish", json_data=init_payload, user_id=sender_uid)
            time.sleep(0.2)
            _, _, ok_feed2, _, feed_data2, _ = client.request(
                "POST", "note", "/discover/note/list", json_data={"channelId": 3, "cursor": 0}, user_id=sender_uid
            )
            if ok_feed2 and isinstance(feed_data2, dict) and feed_data2.get("data"):
                items2 = feed_data2.get("data")
                if isinstance(items2, list) and len(items2) > 0 and (items2[0].get("noteId") or items2[0].get("id")):
                    TestContext.hot_note_id = int(items2[0].get("noteId") or items2[0].get("id"))

    target_note_id = TestContext.hot_note_id if TestContext.hot_note_id else DEFAULT_HOT_NOTE_ID
    published_comment_ids = []

    # 1. 高并发发表一级评论
    stats_pub_l1 = ScenarioStats("发表一级评论", "评论互动域", "高并发向笔记发表一级评论")
    stats_pub_l1.start()

    def do_pub_l1(idx):
        user_id = 9300000 + idx
        payload = {
            "noteId": target_note_id,
            "content": f"这是自动化高并发压力测试的一级评论 #{idx}",
        }
        lat, status, ok, code, data, err = client.request(
            "POST", "comment", "/comment/publish", json_data=payload, user_id=user_id
        )
        if ok and isinstance(data, dict) and data.get("data"):
            try:
                published_comment_ids.append(int(data.get("data")))
            except Exception:
                pass
        stats_pub_l1.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=min(concurrency, 30)) as ex:
        list(ex.map(do_pub_l1, range(rounds // 2)))
    stats_pub_l1.finish()
    results.append(stats_pub_l1)

    if published_comment_ids:
        TestContext.sample_comment_id = published_comment_ids[0]
    elif not TestContext.sample_comment_id:
        init_c_payload = {"noteId": target_note_id, "content": "用于二级子评论与点赞的预热基准一级评论"}
        _, _, ok_c, _, data_c, _ = client.request("POST", "comment", "/comment/publish", json_data=init_c_payload, user_id=sender_uid)
        if ok_c and isinstance(data_c, dict) and data_c.get("data"):
            try:
                TestContext.sample_comment_id = int(data_c.get("data"))
            except Exception:
                pass

    sample_comment_id = TestContext.sample_comment_id if TestContext.sample_comment_id else 10001

    # 2. 高并发发表二级回复评论
    stats_pub_l2 = ScenarioStats("发表二级评论回复", "评论互动域", "高并发回复指定一级评论(二级评论)")
    stats_pub_l2.start()

    def do_pub_l2(idx):
        user_id = 9300000 + idx
        payload = {
            "noteId": target_note_id,
            "content": f"回复 #{idx}：二级子评论高并发回复压测！",
            "replyCommentId": sample_comment_id,
        }
        lat, status, ok, code, data, err = client.request(
            "POST", "comment", "/comment/publish", json_data=payload, user_id=user_id
        )
        stats_pub_l2.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=min(concurrency, 30)) as ex:
        list(ex.map(do_pub_l2, range(rounds // 2)))
    stats_pub_l2.finish()
    results.append(stats_pub_l2)

    # 3. 评论列表热度排序高频拉取
    stats_list = ScenarioStats("热度评论列表查询", "评论互动域", "笔记下热度评论列表高频读取")
    stats_list.start()

    def do_list(_):
        payload = {"noteId": target_note_id, "pageNo": 1, "pageSize": 10}
        lat, status, ok, code, data, err = client.request(
            "POST", "comment", "/comment/list", json_data=payload, user_id=sender_uid
        )
        stats_list.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_list, range(rounds)))
    stats_list.finish()
    results.append(stats_list)

    # 4. 二级子评论列表展开查询
    stats_child_list = ScenarioStats("二级子评论展开查询", "评论互动域", "二级评论展开列表分页查询")
    stats_child_list.start()

    def do_child_list(_):
        payload = {"parentCommentId": sample_comment_id, "pageNo": 1}
        lat, status, ok, code, data, err = client.request(
            "POST", "comment", "/comment/child/list", json_data=payload, user_id=sender_uid
        )
        stats_child_list.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_child_list, range(rounds)))
    stats_child_list.finish()
    results.append(stats_child_list)

    # 5. 高并发评论点赞
    stats_like = ScenarioStats("高并发评论点赞", "评论互动域", "多并发用户同时对热门评论点赞")
    stats_like.start()

    def do_like(idx):
        user_id = 9400000 + idx
        payload = {"commentId": sample_comment_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "comment", "/comment/like", json_data=payload, user_id=user_id
        )
        stats_like.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_like, range(rounds)))
    stats_like.finish()
    results.append(stats_like)

    # 6. 高并发评论取消点赞
    stats_unlike = ScenarioStats("高并发取消评论点赞", "评论互动域", "多并发用户同时取消评论点赞")
    stats_unlike.start()

    def do_unlike(idx):
        user_id = 9400000 + idx
        payload = {"commentId": sample_comment_id}
        lat, status, ok, code, data, err = client.request(
            "POST", "comment", "/comment/unlike", json_data=payload, user_id=user_id
        )
        stats_unlike.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_unlike, range(rounds)))
    stats_unlike.finish()
    results.append(stats_unlike)

    return results
