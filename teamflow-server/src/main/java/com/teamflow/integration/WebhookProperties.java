package com.teamflow.integration;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 可选的外部 Webhook 配置，用于练习 Java HttpClient。 */
@ConfigurationProperties("teamflow.webhook")
public record WebhookProperties(boolean enabled, URI endpoint, String hmacSecret, Duration timeout) {

    public WebhookProperties {
        Objects.requireNonNull(endpoint, "Webhook 地址不能为 null");
        Objects.requireNonNull(timeout, "Webhook 超时不能为 null");
        if (!endpoint.isAbsolute() || endpoint.getHost() == null) {
            throw new IllegalArgumentException("Webhook 地址必须是绝对 HTTP(S) URI");
        }
        String scheme = endpoint.getScheme();
        if (!"https".equalsIgnoreCase(scheme)
                && !("http".equalsIgnoreCase(scheme)
                && isLoopback(endpoint.getHost()))) {
            throw new IllegalArgumentException(
                    "Webhook 只允许 HTTPS，本地回环测试可使用 HTTP"
            );
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Webhook 超时必须大于 0");
        }
        if (enabled && (hmacSecret == null || hmacSecret.isBlank())) {
            throw new IllegalArgumentException(
                    "启用 Webhook 时 HMAC 密钥不能为空"
            );
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }
}
