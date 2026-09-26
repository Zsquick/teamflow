package com.teamflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 任务事件、通知消费与死信拓扑。 */
@Configuration(proxyBeanMethods = false)
public class RabbitMqConfig {

    public static final String TASK_EXCHANGE = "teamflow.task.exchange";
    public static final String TEAM_EXCHANGE = "teamflow.team.exchange";
    public static final String NOTIFICATION_QUEUE =
            "teamflow.notification.queue";
    public static final String ASSIGNMENT_ROUTING_KEY =
            "task.assignment.changed";
    public static final String INVITATION_QUEUE =
            "teamflow.invitation.notification.queue";
    public static final String INVITATION_ROUTING_KEY =
            "team.invitation.changed";
    public static final String DEAD_LETTER_EXCHANGE =
            "teamflow.dead-letter.exchange";
    public static final String DEAD_LETTER_QUEUE =
            "teamflow.dead-letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY =
            "notification.dead";

    private static final int NOTIFICATION_MESSAGE_TTL_MILLIS =
            24 * 60 * 60 * 1000;

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange teamExchange() {
        return new DirectExchange(TEAM_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument(
                        "x-dead-letter-exchange",
                        DEAD_LETTER_EXCHANGE
                )
                .withArgument(
                        "x-dead-letter-routing-key",
                        DEAD_LETTER_ROUTING_KEY
                )
                .withArgument(
                        "x-message-ttl",
                        NOTIFICATION_MESSAGE_TTL_MILLIS
                )
                .build();
    }

    @Bean
    public Binding notificationBinding(
            @Qualifier("notificationQueue") Queue notificationQueue,
            @Qualifier("taskExchange") DirectExchange taskExchange
    ) {
        return BindingBuilder.bind(notificationQueue)
                .to(taskExchange)
                .with(ASSIGNMENT_ROUTING_KEY);
    }

    @Bean
    public Queue invitationQueue() {
        return QueueBuilder.durable(INVITATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .withArgument("x-message-ttl", NOTIFICATION_MESSAGE_TTL_MILLIS)
                .build();
    }

    @Bean
    public Binding invitationBinding(
            @Qualifier("invitationQueue") Queue invitationQueue,
            @Qualifier("teamExchange") DirectExchange teamExchange
    ) {
        return BindingBuilder.bind(invitationQueue)
                .to(teamExchange)
                .with(INVITATION_ROUTING_KEY);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(
            @Qualifier("deadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange")
            DirectExchange deadLetterExchange
    ) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    /** 使用受信任包受限的 Jackson JSON 消息转换器。 */
    @Bean
    public MessageConverter rabbitMessageConverter(
            ObjectMapper objectMapper
    ) {
        DefaultJackson2JavaTypeMapper typeMapper =
                new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.teamflow.messaging");

        Jackson2JsonMessageConverter converter =
                new Jackson2JsonMessageConverter(objectMapper);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
