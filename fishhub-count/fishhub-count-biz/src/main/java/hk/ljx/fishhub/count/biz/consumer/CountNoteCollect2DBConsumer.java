package hk.ljx.fishhub.count.biz.consumer;

import cn.hutool.core.collection.CollUtil;
import com.google.common.util.concurrent.RateLimiter;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.biz.constant.MQConstants;
import hk.ljx.fishhub.count.biz.constant.RedisKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.AggregationCountCollectUnCollectNoteMqDTO;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
@RocketMQMessageListener(consumerGroup = "fishhub_group_" + MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB, // Group 组
        topic = MQConstants.TOPIC_COUNT_NOTE_COLLECT_2_DB // 主题 Topic
        )
@Slf4j
public class CountNoteCollect2DBConsumer implements RocketMQListener<String> {

    @Resource
    private NoteCountDOMapper noteCountDOMapper;
    @Resource
    private UserCountDOMapper userCountDOMapper;
    @Resource
    private MqIdempotentExecutor mqIdempotentExecutor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserCountCacheVersionService userCountCacheVersionService;

    // 每秒创建 5000 个令牌
    private RateLimiter rateLimiter = RateLimiter.create(5000);

    @Override
    public void onMessage(String body) {
        // 流量削峰：通过获取令牌，如果没有令牌可用，将阻塞，直到获得
        rateLimiter.acquire();

        log.info("## 消费到了 MQ 【计数: 笔记收藏数入库】, {}...", body);

        List<AggregationCountCollectUnCollectNoteMqDTO> countList = null;
        try {
            countList = JsonUtils.parseList(body, AggregationCountCollectUnCollectNoteMqDTO.class);
        } catch (Exception e) {
            log.error("## 解析 JSON 字符串异常", e);
        }

        if (CollUtil.isEmpty(countList) || countList.stream().anyMatch(item -> item.getNoteId() == null
                || item.getCreatorId() == null || item.getCount() == null || item.getBatchId() == null)) {
            throw new IllegalArgumentException("笔记收藏计数消息为空或格式错误");
        }
        List<AggregationCountCollectUnCollectNoteMqDTO> finalCountList = countList;
        boolean applied = mqIdempotentExecutor.execute("count-note-collect-2db", body, () -> {
            // 1. 在内存中按 noteId 聚合增量，并严格按 noteId 升序更新，消除 MySQL 行锁交叉死锁
            finalCountList.stream()
                    .collect(Collectors.groupingBy(AggregationCountCollectUnCollectNoteMqDTO::getNoteId,
                            Collectors.summingInt(AggregationCountCollectUnCollectNoteMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> noteCountDOMapper.insertOrUpdateCollectTotalByNoteId(entry.getValue(), entry.getKey()));

            // 2. 在内存中按 creatorId 聚合增量，并严格按 creatorId 升序更新，消除 MySQL 行锁交叉死锁
            finalCountList.stream()
                    .collect(Collectors.groupingBy(AggregationCountCollectUnCollectNoteMqDTO::getCreatorId,
                            Collectors.summingInt(AggregationCountCollectUnCollectNoteMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> userCountDOMapper.insertOrUpdateCollectTotalByUserId(entry.getValue(), entry.getKey()));
        });
        stringRedisTemplate.delete(countList.stream()
                .map(item -> RedisKeyConstants.buildCountNoteKey(item.getNoteId()))
                .distinct()
                .toList());
        countList.stream().map(AggregationCountCollectUnCollectNoteMqDTO::getCreatorId).distinct()
                .forEach(userCountCacheVersionService::advanceVersion);
        if (!applied) {
            log.info("笔记收藏计数消息已处理，忽略重复投递");
        }
    }

}
