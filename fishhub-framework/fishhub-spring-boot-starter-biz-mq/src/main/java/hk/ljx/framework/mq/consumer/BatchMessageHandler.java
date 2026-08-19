package hk.ljx.framework.mq.consumer;

import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

/**
 * 批量消息处理器。实现需要对整批消息负责：
 * <ul>
 *     <li>返回 {@code true} —— 整批消费成功，向 RocketMQ 确认（ACK）；</li>
 *     <li>返回 {@code false} —— 整批稍后重投（并发模式）或暂时挂起当前队列（顺序模式）。</li>
 * </ul>
 */
@FunctionalInterface
public interface BatchMessageHandler {

    /**
     * 处理一批消息。
     *
     * @param messages 本批拉取到的消息
     * @return true=整批成功确认；false=整批稍后重投/挂起
     */
    boolean handle(List<MessageExt> messages);
}
