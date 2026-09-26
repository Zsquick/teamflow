package com.teamflow.integration.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;
import com.teamflow.messaging.RabbitMqConfig;
import com.teamflow.messaging.TaskAssignmentChangedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 RabbitMQ 3.13 验证 JSON、路由、确认和死信拓扑。 */
@Testcontainers(disabledWithoutDocker = true)
class RabbitTopologyIntegrationTest {

    private static final String NOW = "2026-09-18T01:02:03.456Z";

    @Container
    private static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(
                    com.teamflow.integration.TestContainerImages.RABBITMQ
            );

    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;
    private static RabbitAdmin rabbitAdmin;

    @BeforeAll
    static void setUpTopology() {
        connectionFactory = new CachingConnectionFactory(
                RABBITMQ.getHost(),
                RABBITMQ.getAmqpPort()
        );
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());
        connectionFactory.setPublisherConfirmType(
                CachingConnectionFactory.ConfirmType.CORRELATED
        );
        connectionFactory.setPublisherReturns(true);

        RabbitMqConfig config = new RabbitMqConfig();
        DirectExchange taskExchange = config.taskExchange();
        DirectExchange deadLetterExchange = config.deadLetterExchange();
        Queue notificationQueue = config.notificationQueue();
        Queue deadLetterQueue = config.deadLetterQueue();
        Binding notificationBinding = config.notificationBinding(
                notificationQueue,
                taskExchange
        );
        Binding deadLetterBinding = config.deadLetterBinding(
                deadLetterQueue,
                deadLetterExchange
        );

        rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.declareExchange(taskExchange);
        rabbitAdmin.declareExchange(deadLetterExchange);
        rabbitAdmin.declareQueue(notificationQueue);
        rabbitAdmin.declareQueue(deadLetterQueue);
        rabbitAdmin.declareBinding(notificationBinding);
        rabbitAdmin.declareBinding(deadLetterBinding);

        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setMessageConverter(config.rabbitMessageConverter(
                new ObjectMapper().findAndRegisterModules()
        ));
        rabbitTemplate.setReceiveTimeout(Duration.ofSeconds(5).toMillis());
    }

    @BeforeEach
    void purgeQueues() {
        rabbitAdmin.purgeQueue(RabbitMqConfig.NOTIFICATION_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitMqConfig.DEAD_LETTER_QUEUE, false);
    }

    @AfterAll
    static void closeConnectionFactory() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldRouteJsonEventAndReceivePublisherAck() throws Exception {
        TaskAssignmentChangedEvent event = event("event-routing");
        CorrelationData correlationData = new CorrelationData(event.eventId());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.TASK_EXCHANGE,
                RabbitMqConfig.ASSIGNMENT_ROUTING_KEY,
                event,
                correlationData
        );

        CorrelationData.Confirm confirm = correlationData.getFuture()
                .get(10, TimeUnit.SECONDS);
        Object received = rabbitTemplate.receiveAndConvert(
                RabbitMqConfig.NOTIFICATION_QUEUE,
                5000
        );

        assertTrue(confirm.isAck());
        assertEquals(event, received);
    }

    @Test
    void shouldDeadLetterRejectedBusinessMessage() throws Exception {
        TaskAssignmentChangedEvent event = event("event-dead-letter");
        CorrelationData correlationData = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.TASK_EXCHANGE,
                RabbitMqConfig.ASSIGNMENT_ROUTING_KEY,
                event,
                correlationData
        );
        assertTrue(correlationData.getFuture()
                .get(10, TimeUnit.SECONDS)
                .isAck());

        GetResponse delivery = rabbitTemplate.execute(channel -> {
            GetResponse response = channel.basicGet(
                    RabbitMqConfig.NOTIFICATION_QUEUE,
                    false
            );
            if (response != null) {
                channel.basicReject(
                        response.getEnvelope().getDeliveryTag(),
                        false
                );
            }
            return response;
        });
        assertNotNull(delivery);

        Object deadLetter = rabbitTemplate.receiveAndConvert(
                RabbitMqConfig.DEAD_LETTER_QUEUE,
                5000
        );
        assertEquals(event, deadLetter);
    }

    private static TaskAssignmentChangedEvent event(String eventId) {
        return new TaskAssignmentChangedEvent(
                eventId,
                "t001",
                "p001",
                1,
                null,
                "u002",
                "u001",
                NOW
        );
    }
}
