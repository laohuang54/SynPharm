package com.synpharm.mq;

import com.rabbitmq.client.Channel;
import com.synpharm.service.BatchProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 批量任务消息消费者。
 *
 * <p>监听 batch.task.queue，手动 ack：
 * <ul>
 *   <li>处理成功 → basicAck</li>
 *   <li>处理失败 → basicNack(requeue=false)，消息进入死信队列 batch.task.dlq</li>
 * </ul>
 *
 * <p>【临时关闭】RabbitMQ 监听已暂时停用：
 * <ul>
 *   <li>@RabbitListener 已注释，方法不会被 Spring 注册为 MQ 消费端</li>
 *   <li>如需恢复，取消本方法 @RabbitListener 的注释，并同时恢复 RabbitConfig 中 @EnableRabbit 的注释，
 *       然后确保 application.yml 配置了 spring.rabbitmq.* 连接参数并启动 RabbitMQ 服务</li>
 * </ul>
 *
 * @author SynPharm Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchTaskConsumer {

    private final BatchProcessService batchProcessService;

    // @RabbitListener(queues = RabbitConfig.BATCH_QUEUE, ackMode = "MANUAL") // TODO 暂时关闭 RabbitMQ 监听：如需恢复，取消本行注释，并恢复 RabbitConfig 的 @EnableRabbit
    public void onBatchTask(BatchTaskMessage message, Channel channel, Message amqpMessage) throws IOException {
        String batchId = message.getBatchId();
        String algoType = message.getAlgoType();
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();

        log.info("消费批量任务消息: batchId={}, algoType={}", batchId, algoType);
        try {
            batchProcessService.processBatch(batchId, algoType);
            channel.basicAck(deliveryTag, false);
            log.info("批量任务处理成功并确认: batchId={}", batchId);
        } catch (Exception e) {
            log.error("批量任务处理失败, 进入死信队列: batchId={}, error={}", batchId, e.getMessage());
            // 不 requeue，进死信队列（由补偿/人工处理）
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
