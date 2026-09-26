package com.teamflow.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.core.outbox.domain.OutboxEvent;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 发送 Outbox 事件，并同步等待 RabbitMQ confirm/return 结果。 */
@Component
public class RabbitTaskEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RabbitTaskEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = Objects.requireNonNull(
                rabbitTemplate,
                "RabbitTemplate 不能为 null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "ObjectMapper 不能为 null"
        );
        this.rabbitTemplate.setMandatory(true);
    }

    /** 便于不需要邀请载荷的单元测试继续构造发布器。 */
    RabbitTaskEventPublisher(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, new ObjectMapper().findAndRegisterModules());
    }

    /** 发送事件；只在 broker 确认且消息可路由时返回。 */
    public void publishAndConfirm(
            OutboxEvent event,
            Duration confirmTimeout
    ) {
        Objects.requireNonNull(event, "Outbox 事件不能为 null");
        requirePositive(confirmTimeout, "RabbitMQ 确认超时");
        Object transportEvent = toTransportEvent(event);
        String exchange = exchange(event);
        String routingKey = routingKey(event);
        CorrelationData correlationData = new CorrelationData(event.eventKey());
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                transportEvent,
                message -> {
                    message.getMessageProperties().setMessageId(
                            event.eventKey()
                    );
                    String aggregateHeader = OutboxEvent.TEAM_INVITATION_CHANGED
                            .equals(event.eventType())
                            ? "teamflow-invitation-id"
                            : "teamflow-task-id";
                    message.getMessageProperties().setHeader(
                            aggregateHeader,
                            event.taskId()
                    );
                    if (event.assigneeId() != null) {
                        message.getMessageProperties().setHeader(
                                "teamflow-assignee-id",
                                event.assigneeId()
                        );
                    }
                    message.getMessageProperties().setHeader(
                            "teamflow-occurred-at",
                            event.occurredAt()
                    );
                    return message;
                },
                correlationData
        );

        CorrelationData.Confirm confirm = awaitConfirm(
                correlationData,
                confirmTimeout
        );
        ReturnedMessage returned = correlationData.getReturned();
        if (returned != null) {
            throw new EventPublishException(
                    "RabbitMQ 无法路由事件: replyCode="
                            + returned.getReplyCode()
                            + ", replyText="
                            + returned.getReplyText()
            );
        }
        if (confirm == null || !confirm.isAck()) {
            throw new EventPublishException(
                    "RabbitMQ 拒绝事件: "
                            + (confirm == null ? "未返回确认" : confirm.getReason())
            );
        }
    }

    private static CorrelationData.Confirm awaitConfirm(
            CorrelationData correlationData,
            Duration timeout
    ) {
        try {
            return correlationData.getFuture().get(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(
                    "等待 RabbitMQ 确认时线程被中断",
                    exception
            );
        } catch (TimeoutException exception) {
            throw new EventPublishException(
                    "等待 RabbitMQ 确认超时",
                    exception
            );
        } catch (ExecutionException exception) {
            throw new EventPublishException(
                    "RabbitMQ 发布确认失败",
                    exception.getCause() == null
                            ? exception
                            : exception.getCause()
            );
        }
    }

    private Object toTransportEvent(OutboxEvent event) {
        if (OutboxEvent.TASK_ASSIGNEE_CHANGED.equals(event.eventType())) {
            return toTaskTransportEvent(event);
        }
        if (OutboxEvent.TEAM_INVITATION_CHANGED.equals(event.eventType())) {
            try {
                return objectMapper.readValue(
                        event.eventPayload(),
                        TeamInvitationChangedEvent.class
                );
            } catch (JsonProcessingException exception) {
                throw new EventPublishException(
                        "解析邀请 Outbox 载荷失败",
                        exception
                );
            }
        }
        throw new EventPublishException(
                "不支持的 Outbox 事件类型: " + event.eventType()
        );
    }

    private static TaskAssignmentChangedEvent toTaskTransportEvent(
            OutboxEvent event
    ) {
        return new TaskAssignmentChangedEvent(
                event.eventKey(), event.taskId(), event.projectId(),
                event.taskVersion(), event.previousAssigneeId(),
                event.assigneeId(), event.operatorId(), event.occurredAt()
        );
    }

    private static String exchange(OutboxEvent event) {
        return OutboxEvent.TEAM_INVITATION_CHANGED.equals(event.eventType())
                ? RabbitMqConfig.TEAM_EXCHANGE
                : RabbitMqConfig.TASK_EXCHANGE;
    }

    private static String routingKey(OutboxEvent event) {
        return OutboxEvent.TEAM_INVITATION_CHANGED.equals(event.eventType())
                ? RabbitMqConfig.INVITATION_ROUTING_KEY
                : RabbitMqConfig.ASSIGNMENT_ROUTING_KEY;
    }

    private static void requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }
}
