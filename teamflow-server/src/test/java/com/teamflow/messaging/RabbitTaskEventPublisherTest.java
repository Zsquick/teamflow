package com.teamflow.messaging;

import com.teamflow.core.outbox.domain.OutboxEvent;
import com.teamflow.core.task.event.TaskAssignmentChanged;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 任务事件传输格式、confirm 和 return 测试。 */
class RabbitTaskEventPublisherTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Test
    void shouldReturnOnlyAfterBrokerAckAndRoutableMessage() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(
                Object.class
        );
        ArgumentCaptor<MessagePostProcessor> processorCaptor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor =
                ArgumentCaptor.forClass(CorrelationData.class);
        completeWithConfirm(template, new CorrelationData.Confirm(true, null));
        RabbitTaskEventPublisher publisher =
                new RabbitTaskEventPublisher(template);

        publisher.publishAndConfirm(outboxEvent(), TIMEOUT);

        verify(template).convertAndSend(
                eq(RabbitMqConfig.TASK_EXCHANGE),
                eq(RabbitMqConfig.ASSIGNMENT_ROUTING_KEY),
                eventCaptor.capture(),
                processorCaptor.capture(),
                correlationCaptor.capture()
        );
        TaskAssignmentChangedEvent transport =
                (TaskAssignmentChangedEvent) eventCaptor.getValue();
        Message message = processorCaptor.getValue().postProcessMessage(
                new Message(new byte[0], new MessageProperties())
        );
        assertEquals("t001:4:ASSIGNEE_CHANGED", transport.eventId());
        assertEquals(transport.eventId(), correlationCaptor.getValue().getId());
        assertEquals(
                transport.eventId(),
                message.getMessageProperties().getMessageId()
        );
        assertEquals(
                "t001",
                message.getMessageProperties().getHeader("teamflow-task-id")
        );
        assertNull(message.getMessageProperties().getHeader(
                "teamflow-assignee-id"
        ));
        verify(template).setMandatory(true);
    }

    @Test
    void shouldRejectBrokerNack() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        completeWithConfirm(
                template,
                new CorrelationData.Confirm(false, "exchange unavailable")
        );
        RabbitTaskEventPublisher publisher =
                new RabbitTaskEventPublisher(template);

        assertThrows(
                EventPublishException.class,
                () -> publisher.publishAndConfirm(outboxEvent(), TIMEOUT)
        );
    }

    @Test
    void shouldRejectReturnedMessageEvenWhenBrokerAckedIt() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()),
                    312,
                    "NO_ROUTE",
                    RabbitMqConfig.TASK_EXCHANGE,
                    RabbitMqConfig.ASSIGNMENT_ROUTING_KEY
            ));
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(template).convertAndSend(
                eq(RabbitMqConfig.TASK_EXCHANGE),
                eq(RabbitMqConfig.ASSIGNMENT_ROUTING_KEY),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
        RabbitTaskEventPublisher publisher =
                new RabbitTaskEventPublisher(template);

        assertThrows(
                EventPublishException.class,
                () -> publisher.publishAndConfirm(outboxEvent(), TIMEOUT)
        );
    }

    @Test
    void shouldPropagateImmediateTransportFailureForOutboxRetry() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(template)
                .convertAndSend(
                        eq(RabbitMqConfig.TASK_EXCHANGE),
                        eq(RabbitMqConfig.ASSIGNMENT_ROUTING_KEY),
                        any(),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class)
                );
        RabbitTaskEventPublisher publisher =
                new RabbitTaskEventPublisher(template);

        assertThrows(
                IllegalStateException.class,
                () -> publisher.publishAndConfirm(outboxEvent(), TIMEOUT)
        );
    }

    private static void completeWithConfirm(
            RabbitTemplate template,
            CorrelationData.Confirm confirm
    ) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(confirm);
            return null;
        }).when(template).convertAndSend(
                eq(RabbitMqConfig.TASK_EXCHANGE),
                eq(RabbitMqConfig.ASSIGNMENT_ROUTING_KEY),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    private static OutboxEvent outboxEvent() {
        return OutboxEvent.pending(
                "oe001",
                new TaskAssignmentChanged(
                        "t001",
                        "p001",
                        4,
                        "u002",
                        null,
                        "u001",
                        "2026-09-18T01:02:03.456Z"
                )
        );
    }
}
