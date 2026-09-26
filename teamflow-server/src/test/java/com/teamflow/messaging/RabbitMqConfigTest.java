package com.teamflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RabbitMQ 持久化拓扑和事件 JSON 往返测试。 */
class RabbitMqConfigTest {

    private static final String NOW = "2026-09-18T01:02:03.456Z";

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    void shouldDeclareDurableBusinessAndDeadLetterTopology() {
        DirectExchange taskExchange = config.taskExchange();
        Queue notificationQueue = config.notificationQueue();
        DirectExchange deadLetterExchange = config.deadLetterExchange();
        Queue deadLetterQueue = config.deadLetterQueue();
        Binding businessBinding = config.notificationBinding(
                notificationQueue,
                taskExchange
        );
        Binding deadBinding = config.deadLetterBinding(
                deadLetterQueue,
                deadLetterExchange
        );

        assertTrue(taskExchange.isDurable());
        assertTrue(notificationQueue.isDurable());
        assertTrue(deadLetterExchange.isDurable());
        assertTrue(deadLetterQueue.isDurable());
        assertEquals(
                RabbitMqConfig.DEAD_LETTER_EXCHANGE,
                notificationQueue.getArguments().get(
                        "x-dead-letter-exchange"
                )
        );
        assertEquals(
                RabbitMqConfig.ASSIGNMENT_ROUTING_KEY,
                businessBinding.getRoutingKey()
        );
        assertEquals(
                RabbitMqConfig.DEAD_LETTER_ROUTING_KEY,
                deadBinding.getRoutingKey()
        );
    }

    @Test
    void shouldRoundTripTrustedAssignmentEventAsJson() {
        MessageConverter converter = config.rabbitMessageConverter(
                new ObjectMapper().findAndRegisterModules()
        );
        TaskAssignmentChangedEvent event =
                new TaskAssignmentChangedEvent(
                        "t001:1:ASSIGNEE_CHANGED",
                        "t001",
                        "p001",
                        1,
                        null,
                        "u002",
                        "u001",
                        NOW
                );

        Message message = converter.toMessage(
                event,
                new MessageProperties()
        );
        Object decoded = converter.fromMessage(message);

        assertEquals(event, decoded);
    }
}
