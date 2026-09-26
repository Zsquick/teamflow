package com.teamflow.config;

import com.teamflow.concurrent.ObservableAbortPolicy;
import com.teamflow.integration.WebhookProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Webhook HttpClient 和独立重试线程配置测试。 */
class WebhookConfigTest {

    @Test
    void shouldCreateBoundedHttpClientWithoutRedirects() {
        WebhookProperties properties = new WebhookProperties(
                true,
                URI.create("https://hooks.example.com/events"),
                "secret",
                Duration.ofSeconds(3)
        );

        HttpClient client = new WebhookConfig().webhookHttpClient(properties);

        assertEquals(
                Duration.ofSeconds(3),
                client.connectTimeout().orElseThrow()
        );
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
        assertEquals(HttpClient.Version.HTTP_2, client.version());
    }

    @Test
    void shouldUseDedicatedSingleRetryThread() throws Exception {
        TaskScheduler configured = new WebhookConfig().webhookRetryScheduler();
        ThreadPoolTaskScheduler scheduler = assertInstanceOf(
                ThreadPoolTaskScheduler.class,
                configured
        );
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        scheduler.initialize();
        try {
            scheduler.schedule(() -> {
                threadName.set(Thread.currentThread().getName());
                completed.countDown();
            }, Instant.now());

            assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertTrue(
                    threadName.get().startsWith("teamflow-webhook-retry-")
            );
            assertAll(
                    () -> assertEquals(1, scheduler.getPoolSize()),
                    () -> assertTrue(
                            scheduler.getScheduledThreadPoolExecutor()
                                    .getRemoveOnCancelPolicy()
                    ),
                    () -> assertInstanceOf(
                            ObservableAbortPolicy.class,
                            scheduler.getScheduledThreadPoolExecutor()
                                    .getRejectedExecutionHandler()
                    )
            );
        } finally {
            scheduler.destroy();
        }
    }
}
