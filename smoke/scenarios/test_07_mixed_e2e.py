# -*- coding: utf-8 -*-
"""
场景 7: 全链路高拟真混合压力测试 (E2E Heavy Load)
模拟真实线上生产环境的混合流量模型：
- 60% 读流量：笔记详情、信息流、个人主页、评论列表、搜索
- 20% 互动写流量：点赞/取消点赞、收藏/取消收藏、关注/取关
- 15% 评论写流量：发表一级/二级评论
- 5% 核心创作写流量：发布图文笔记
"""
import random
import time
from concurrent.futures import ThreadPoolExecutor
from ..config import DEFAULT_CONCURRENCY, DEFAULT_ROUNDS, DEFAULT_HOT_NOTE_ID, DEFAULT_TARGET_USER_ID, SEED_USERS, TestContext
from ..stats import ScenarioStats


def run(client, concurrency: int = DEFAULT_CONCURRENCY, rounds: int = DEFAULT_ROUNDS * 2) -> list:
    stats_mixed = ScenarioStats("全链路混合大促压测", "全链路混合域", "模拟生产环境 60%读 + 20%互动 + 15%评论 + 5%发布 混合流量冲击")
    stats_mixed.start()

    target_note_id = TestContext.hot_note_id if TestContext.hot_note_id else DEFAULT_HOT_NOTE_ID
    target_user_id = TestContext.user_ids[0] if TestContext.user_ids else DEFAULT_TARGET_USER_ID

    def do_mixed_op(idx):
        rand = random.random()
        user_id = 9500000 + idx

        # 1. 核心发布 (5%)
        if rand < 0.05:
            payload = {
                "title": f"混合大促压测笔记_{idx}",
                "content": "全链路高拟真混合压力测试自动生成内容...",
                "type": 0,
                "imgUris": ["https://img.fishhub.hk/smoke/cover.jpg"],
                "channelId": 3,
            }
            lat, status, ok, code, data, err = client.request(
                "POST", "note", "/note/publish", json_data=payload, user_id=user_id
            )
        # 2. 评论发布 (15%)
        elif rand < 0.20:
            payload = {
                "noteId": target_note_id,
                "content": f"大促压测评论 #{idx}：性能极佳，响应迅速！",
            }
            lat, status, ok, code, data, err = client.request(
                "POST", "comment", "/comment/publish", json_data=payload, user_id=user_id
            )
        # 3. 互动操作 (点赞/收藏/关注) (20%)
        elif rand < 0.40:
            sub_rand = random.random()
            if sub_rand < 0.4:
                lat, status, ok, code, data, err = client.request(
                    "POST", "note", "/note/like", json_data={"id": target_note_id}, user_id=user_id
                )
            elif sub_rand < 0.7:
                lat, status, ok, code, data, err = client.request(
                    "POST", "note", "/note/collect", json_data={"id": target_note_id}, user_id=user_id
                )
            else:
                lat, status, ok, code, data, err = client.request(
                    "POST", "user", "/relation/follow", json_data={"followUserId": target_user_id}, user_id=user_id
                )
        # 4. 高频读流量 (60%)
        else:
            sub_rand = random.random()
            if sub_rand < 0.35:
                # 笔记详情
                lat, status, ok, code, data, err = client.request(
                    "POST", "note", "/note/detail", json_data={"id": target_note_id}, user_id=user_id
                )
            elif sub_rand < 0.60:
                # 评论流
                lat, status, ok, code, data, err = client.request(
                    "POST", "comment", "/comment/list", json_data={"noteId": target_note_id, "pageNo": 1, "pageSize": 10}, user_id=user_id
                )
            elif sub_rand < 0.80:
                # 发现流
                lat, status, ok, code, data, err = client.request(
                    "POST", "note", "/discover/note/list", json_data={"channelId": 3, "cursor": 0}, user_id=user_id
                )
            else:
                # 关键词检索
                lat, status, ok, code, data, err = client.request(
                    "POST", "search", "/search/note", json_data={"keyword": "Java", "pageNo": 1, "pageSize": 10}, user_id=user_id
                )

        stats_mixed.record(lat, status, ok, code, err)

    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        list(ex.map(do_mixed_op, range(rounds)))

    stats_mixed.finish()
    return [stats_mixed]
