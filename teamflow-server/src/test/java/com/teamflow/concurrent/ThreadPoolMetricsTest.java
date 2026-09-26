package com.teamflow.concurrent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 业务线程池 Micrometer 指标注册测试。 */
class ThreadPoolMetricsTest {

    @Test
    void shouldExposeExecutorStateAndRejectedCount() throws Exception {
        ManagedThreadPoolExecutor executor = executor();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ThreadPoolMetrics(List.of(executor)).bindTo(registry);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();
        Runnable blocked = () -> {
            workerStarted.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            executor.execute(blocked);
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
            executor.execute(() -> {
            });
            assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(
                            () -> rejectedTaskRan.set(true)
                    )
            );

            assertAll(
                    () -> assertEquals(1, gauge(registry, "executor.active")),
                    () -> assertEquals(1, gauge(registry, "executor.queued")),
                    () -> assertEquals(
                            0,
                            gauge(registry, "executor.queue.remaining")
                    ),
                    () -> assertEquals(
                            1,
                            gauge(registry, "executor.pool.size")
                    ),
                    () -> assertEquals(
                            1,
                            gauge(registry, "executor.pool.core")
                    ),
                    () -> assertEquals(
                            1,
                            gauge(registry, "executor.pool.max")
                    ),
                    () -> assertEquals(
                            1,
                            counter(registry, "executor.rejected")
                    ),
                    () -> assertFalse(rejectedTaskRan.get())
            );

            executor.shutdown();
            releaseWorker.countDown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
            assertEquals(
                    2,
                    counter(registry, "executor.completed")
            );
        } finally {
            releaseWorker.countDown();
            executor.forceShutdown();
            registry.close();
        }
    }

    @Test
    void shouldRejectInvalidBinderArguments() {
        ManagedThreadPoolExecutor executor = executor();
        try {
            ThreadPoolMetrics metrics = new ThreadPoolMetrics(
                    List.of(executor)
            );
            assertAll(
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> new ThreadPoolMetrics(null)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new ThreadPoolMetrics(List.of())
                    ),
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> metrics.bindTo(null)
                    )
            );
        } finally {
            executor.forceShutdown();
        }
    }

    private static ManagedThreadPoolExecutor executor() {
        return ThreadPoolFactory.createBounded(
                "metricPool",
                1,
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                "metric-pool-"
        );
    }

    private static double gauge(
            SimpleMeterRegistry registry,
            String metricName
    ) {
        return registry.get(metricName)
                .tag("name", "metricPool")
                .gauge()
                .value();
    }

    private static double counter(
            SimpleMeterRegistry registry,
            String metricName
    ) {
        return registry.get(metricName)
                .tag("name", "metricPool")
                .functionCounter()
                .count();
    }
}
