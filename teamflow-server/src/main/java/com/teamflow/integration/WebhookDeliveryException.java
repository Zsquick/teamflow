package com.teamflow.integration;

/** Webhook 序列化、发送或远端响应不符合约定。 */
public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message) {
        super(message);
    }

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
