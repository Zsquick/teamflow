package com.teamflow.messaging;

/** RabbitMQ 发布、确认或路由失败。 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message) {
        super(message);
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
