package hk.ljx.fishhub.count.biz.consumer.aggregation;

import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.AggregationNoteCountMqDTO;
import hk.ljx.fishhub.count.biz.model.dto.CountNoteMqDTO;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记计数聚合落库抽象基类
 */
@Slf4j
public abstract class AbstractNoteCountAggregationConsumer {

    protected final NoteCountDOMapper noteCountDOMapper;
    protected final UserCountDOMapper userCountDOMapper;
    protected final MqIdempotentExecutor mqIdempotentExecutor;
    protected final StringRedisTemplate stringRedisTemplate;
    protected final UserCountCacheVersionService userCountCacheVersionService;

    protected AbstractNoteCountAggregationConsumer(NoteCountDOMapper noteCountDOMapper,
                                                   UserCountDOMapper userCountDOMapper,
                                                   MqIdempotentExecutor mqIdempotentExecutor,
                                                   StringRedisTemplate stringRedisTemplate,
                                                   UserCountCacheVersionService userCountCacheVersionService) {
        this.noteCountDOMapper = noteCountDOMapper;
        this.userCountDOMapper = userCountDOMapper;
        this.mqIdempotentExecutor = mqIdempotentExecutor;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userCountCacheVersionService = userCountCacheVersionService;
    }

    /**
     * 幂等分组键
     */
    protected abstract String idempotentGroup();

    /**
     * 业务标签
     */
    protected abstract String bizLabel();

    /**
     * 操作类型是否合法
     */
    protected abstract boolean isValidType(Integer type);

    /**
     * 操作类型对应的净增量
     */
    protected abstract int deltaOf(Integer type);

    /**
     * 笔记维度计数更新回调
     */
    protected abstract void updateNoteCount(Long noteId, int delta);

    /**
     * 用户维度计数更新回调
     */
    protected abstract void updateUserCount(Long userId, int delta);

    /**
     * 批量消费并更新数据库与缓存
     *
     * @param bodys 消息体列表
     */
    protected final void consumeBatches(List<String> bodys) {
        log.info("==> 【{}】聚合落库消息, size: {}", bizLabel(), bodys.size());

        // 计算批次 batchId
        String batchId = cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                bodys.stream().sorted().collect(Collectors.joining("|")));

        List<CountNoteMqDTO> events = bodys.stream()
                .flatMap(body -> parseEvents(body).stream())
                .toList();
        if (events.stream().anyMatch(item -> item == null || item.getNoteId() == null
                || item.getNoteCreatorId() == null || !isValidType(item.getType()))) {
            throw new IllegalArgumentException(bizLabel() + "计数消息缺少必要字段或操作类型无效");
        }

        // 按笔记 ID 分组
        Map<Long, List<CountNoteMqDTO>> groupMap = events.stream()
                .collect(Collectors.groupingBy(CountNoteMqDTO::getNoteId));

        // 汇总计数增量
        List<AggregationNoteCountMqDTO> countList = new ArrayList<>();
        for (Map.Entry<Long, List<CountNoteMqDTO>> entry : groupMap.entrySet()) {
            Long noteId = entry.getKey();
            Long creatorId = null;
            int finalCount = 0;
            for (CountNoteMqDTO event : entry.getValue()) {
                creatorId = event.getNoteCreatorId();
                finalCount += deltaOf(event.getType());
            }
            countList.add(AggregationNoteCountMqDTO.builder()
                    .noteId(noteId)
                    .creatorId(creatorId)
                    .count(finalCount)
                    .batchId(batchId)
                    .build());
        }

        log.info("## 【{}】聚合后的计数数据: {}", bizLabel(), JsonUtils.toJsonString(countList));

        // 幂等写入数据库
        boolean applied = mqIdempotentExecutor.execute(idempotentGroup(), batchId, () -> {
            // 1. 按 noteId 升序更新笔记计数
            countList.stream()
                    .collect(Collectors.groupingBy(AggregationNoteCountMqDTO::getNoteId,
                            Collectors.summingInt(AggregationNoteCountMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> updateNoteCount(entry.getKey(), entry.getValue()));

            // 2. 按 creatorId 升序更新用户计数
            countList.stream()
                    .collect(Collectors.groupingBy(AggregationNoteCountMqDTO::getCreatorId,
                            Collectors.summingInt(AggregationNoteCountMqDTO::getCount)))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> updateUserCount(entry.getKey(), entry.getValue()));
        });

        stringRedisTemplate.delete(countList.stream()
                .map(item -> CountKeyConstants.buildCountNoteKey(item.getNoteId()))
                .distinct()
                .toList());
        countList.stream().map(AggregationNoteCountMqDTO::getCreatorId).distinct()
                .forEach(userCountCacheVersionService::advanceVersion);
        if (!applied) {
            log.info("{}计数消息已处理，忽略重复投递, batchId={}", bizLabel(), batchId);
        }
    }

    private List<CountNoteMqDTO> parseEvents(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            try {
                return JsonUtils.parseList(trimmed, CountNoteMqDTO.class);
            } catch (Exception e) {
                throw new IllegalArgumentException(bizLabel() + "计数消息格式错误", e);
            }
        }
        return List.of(JsonUtils.parseObject(trimmed, CountNoteMqDTO.class));
    }
}
