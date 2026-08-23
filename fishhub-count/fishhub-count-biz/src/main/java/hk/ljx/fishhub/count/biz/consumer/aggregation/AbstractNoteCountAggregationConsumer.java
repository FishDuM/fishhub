package hk.ljx.fishhub.count.biz.consumer.aggregation;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.framework.common.util.JsonUtils;
import hk.ljx.fishhub.count.constant.CountKeyConstants;
import hk.ljx.fishhub.count.biz.domain.mapper.NoteCountDOMapper;
import hk.ljx.fishhub.count.biz.domain.mapper.UserCountDOMapper;
import hk.ljx.fishhub.count.biz.model.dto.CountNoteMqDTO;
import hk.ljx.fishhub.count.biz.service.UserCountCacheVersionService;
import hk.ljx.framework.mq.idempotent.MqIdempotentExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
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

        List<CountNoteMqDTO> events = bodys.stream()
                .flatMap(body -> parseEvents(body).stream())
                .toList();
        if (events.stream().anyMatch(item -> item == null || item.getNoteId() == null
                || item.getNoteCreatorId() == null || item.getEventId() == null
                || item.getEventId().isBlank() || !isValidType(item.getType()))) {
            throw new IllegalArgumentException(bizLabel() + "计数消息缺少必要字段或操作类型无效");
        }

        // 事件级幂等：以 sha256(eventId) 为键，只对本次新增事件累加，批次重组/重投不重复计数
        // 注意：executeBatch 内部会再对传入的“消息身份”做一次 sha256，因此这里必须传原始 eventId，
        // 用 sha256(eventId) -> event 的反向映射承接 freshKeys。
        Map<String, CountNoteMqDTO> eventByKey = new HashMap<>();
        List<String> eventIds = new ArrayList<>();
        for (CountNoteMqDTO event : events) {
            eventIds.add(event.getEventId());
            eventByKey.put(DigestUtil.sha256Hex(event.getEventId()), event);
        }

        boolean applied = mqIdempotentExecutor.executeBatch(idempotentGroup(), eventIds, freshKeys -> {
            List<CountNoteMqDTO> freshEvents = freshKeys.stream()
                    .map(eventByKey::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (freshEvents.isEmpty()) {
                return false;
            }
            // 1. 按 noteId 升序聚合并更新笔记计数
            freshEvents.stream()
                    .collect(Collectors.groupingBy(
                            CountNoteMqDTO::getNoteId,
                            TreeMap::new,
                            Collectors.summingInt(e -> deltaOf(e.getType()))
                    ))
                    .forEach((noteId, delta) -> {
                        if (delta != 0) {
                            updateNoteCount(noteId, delta);
                        }
                    });

            // 2. 按 creatorId 升序聚合并更新用户计数
            freshEvents.stream()
                    .collect(Collectors.groupingBy(
                            CountNoteMqDTO::getNoteCreatorId,
                            TreeMap::new,
                            Collectors.summingInt(e -> deltaOf(e.getType()))
                    ))
                    .forEach((creatorId, delta) -> {
                        if (delta != 0) {
                            updateUserCount(creatorId, delta);
                        }
                    });
            return true;
        });

        // 无论是否重复投递，都失效对应计数缓存并推进版本，确保读侧尽快看到最新值
        List<String> noteCountKeys = events.stream().map(CountNoteMqDTO::getNoteId).distinct()
                .map(CountKeyConstants::buildCountNoteKey)
                .toList();
        if (!noteCountKeys.isEmpty()) {
            stringRedisTemplate.delete(noteCountKeys);
        }
        events.stream().map(CountNoteMqDTO::getNoteCreatorId).distinct()
                .forEach(userCountCacheVersionService::advanceVersion);
        if (!applied) {
            log.info("{}计数消息已处理，忽略重复投递, eventCount={}", bizLabel(), events.size());
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
