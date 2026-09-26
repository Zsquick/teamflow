package com.teamflow.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.core.common.time.UtcTimeText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.http.HttpClient;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * 可选的异步 Webhook 客户端。
 * 正文只序列化一次，签名和实际发送使用完全相同的字节。
 */
@Component
public class WebhookClient {

    private static final Logger log = LoggerFactory.getLogger(
            WebhookClient.class
    );
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(250);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HmacSigner hmacSigner;
    private final WebhookProperties properties;
    private final TaskScheduler retryScheduler;
    private final Clock clock;

    public WebhookClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            HmacSigner hmacSigner,
            WebhookProperties properties,
            @Qualifier("webhookRetryScheduler")
            TaskScheduler retryScheduler,
            Clock clock
    ) {
        this.httpClient = Objects.requireNonNull(
                httpClient,
                "HTTP 客户端不能为 null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "JSON 映射器不能为 null"
        );
        this.hmacSigner = Objects.requireNonNull(
                hmacSigner,
                "HMAC 签名器不能为 null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "Webhook 配置不能为 null"
        );
        this.retryScheduler = Objects.requireNonNull(
                retryScheduler,
                "Webhook 重试调度器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "时钟不能为 null");
    }

    public CompletableFuture<Void> sendTaskEvent(Object event) {
        Objects.requireNonNull(event, "Webhook 事件不能为 null");
        if (!properties.enabled()) {
            return CompletableFuture.completedFuture(null);
        }

        final byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException exception) {
            return CompletableFuture.failedFuture(
                    new WebhookDeliveryException(
                            "Webhook 事件无法序列化",
                            exception
                    )
            );
        }
        String eventId = resolveEventId(payload);
        String occurredAt = UtcTimeText.now(clock);
        String signature = "sha256=" + hmacSigner.sign(
                payload,
                properties.hmacSecret()
        );
        return sendAttempt(
                payload,
                eventId,
                occurredAt,
                signature,
                1
        );
    }

    private CompletableFuture<Void> sendAttempt(
            byte[] payload,
            String eventId,
            String occurredAt,
            String signature,
            int attempt
    ) {
        HttpRequest request = HttpRequest.newBuilder(properties.endpoint())
                .timeout(properties.timeout())
                .header("Content-Type", "application/json")
                .header("X-TeamFlow-Event-Id", eventId)
                .header("X-TeamFlow-Timestamp", occurredAt)
                .header("X-TeamFlow-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        final CompletableFuture<HttpResponse<Void>> responseFuture;
        try {
            responseFuture = httpClient.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.discarding()
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(
                    nonRetryableFailure(exception)
            );
        }

        return responseFuture.handle((response, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                if (!isRetryableFailure(cause)) {
                    return CompletableFuture.<Void>failedFuture(
                            nonRetryableFailure(cause)
                    );
                }
                return retryOrFail(
                        payload,
                        eventId,
                        occurredAt,
                        signature,
                        attempt,
                        cause
                );
            }
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            WebhookDeliveryException exception =
                    new WebhookDeliveryException(
                            "Webhook 返回 HTTP " + status
                    );
            if (isRetryableStatus(status)) {
                return retryOrFail(
                        payload,
                        eventId,
                        occurredAt,
                        signature,
                        attempt,
                        exception
                );
            }
            return CompletableFuture.<Void>failedFuture(exception);
        }).thenCompose(future -> future);
    }

    private CompletableFuture<Void> retryOrFail(
            byte[] payload,
            String eventId,
            String occurredAt,
            String signature,
            int attempt,
            Throwable failure
    ) {
        if (attempt >= MAX_ATTEMPTS) {
            log.error(
                    "Webhook 发送失败 eventId={} attempts={}",
                    eventId,
                    attempt,
                    failure
            );
            return CompletableFuture.failedFuture(
                    new WebhookDeliveryException(
                            "Webhook 在有限重试后仍发送失败",
                            failure
                    )
            );
        }

        Duration backoff = INITIAL_BACKOFF.multipliedBy(
                1L << (attempt - 1)
        );
        CompletableFuture<Void> delay = new CompletableFuture<>();
        try {
            retryScheduler.schedule(
                    () -> delay.complete(null),
                    Instant.now(clock).plus(backoff)
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(
                    new WebhookDeliveryException(
                            "Webhook 重试任务无法调度",
                            exception
                    )
            );
        }
        return delay.thenCompose(ignored -> sendAttempt(
                payload,
                eventId,
                occurredAt,
                signature,
                attempt + 1
        ));
    }

    private String resolveEventId(byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode idNode = root == null ? null : root.get("eventId");
            if (idNode != null && idNode.isTextual()) {
                String candidate = idNode.textValue().strip();
                if (!candidate.isEmpty()
                        && candidate.length() <= 200
                        && candidate.chars().allMatch(
                        value -> value >= 0x21 && value <= 0x7E
                )) {
                    return candidate;
                }
            }
        } catch (IOException ignored) {
            // writeValueAsBytes 已成功；读取树失败时使用正文摘要作为稳定编号。
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前 JVM 不支持 SHA-256",
                    exception
            );
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isRetryableFailure(Throwable failure) {
        return failure instanceof IOException
                || failure instanceof TimeoutException;
    }

    private static WebhookDeliveryException nonRetryableFailure(
            Throwable failure
    ) {
        return new WebhookDeliveryException(
                "Webhook 发送遇到不可重试错误",
                failure
        );
    }

    /**
     * 只重试限流以及通常表示网关或服务暂时不可用的响应。
     * 501、505 等永久性协议错误继续发送也不会自行恢复，因此立即失败。
     */
    private static boolean isRetryableStatus(int status) {
        return status == 429
                || status == 500
                || status == 502
                || status == 503
                || status == 504;
    }
}
