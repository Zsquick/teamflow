package com.teamflow.concurrent;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 业务线程池工厂和受控关闭测试。 */
class ThreadPoolFactoryTest {

    @Test
    void shouldCreateNamedBoundedExecutorAndShutdownGracefully()
            throws Exception {
        ManagedThreadPoolExecutor executor = ThreadPoolFactory.createBounded(
                "testPool",
                1,
                2,
                3,
                Duration.ofSeconds(7),
                Duration.ofSeconds(1),
                "test-worker-"
        );
        try {
            String threadName = executor.submit(
                    () -> Thread.currentThread().getName()
            ).get(1, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(1, executor.getCorePoolSize()),
                    () -> assertEquals(2, executor.getMaximumPoolSize()),
                    () -> assertEquals(3, executor.getQueue().remainingCapacity()),
                    () -> assertEquals(
                            7_000L,
                            executor.getKeepAliveTime(TimeUnit.MILLISECONDS)
                    ),
                    () -> assertTrue(executor.allowsCoreThreadTimeOut()),
                    () -> assertTrue(threadName.startsWith("test-worker-")),
                    () -> assertInstanceOf(
                            ObservableAbortPolicy.class,
                            executor.getRejectedExecutionHandler()
                    )
            );
        } finally {
            executor.shutdownGracefully();
        }

        assertAll(
                () -> assertTrue(executor.isShutdown()),
                () -> assertTrue(executor.isTerminated())
        );
    }

    @Test
    void shouldInterruptRunningTaskAfterGracefulWaitExpires()
            throws Exception {
        ManagedThreadPoolExecutor executor = ThreadPoolFactory.createBounded(
                "shortLivedPool",
                1,
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ofMillis(10),
                "short-lived-"
        );
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        executor.execute(() -> {
            started.countDown();
            try {
                neverReleased.await();
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(1, TimeUnit.SECONDS));

        executor.shutdownGracefully();

        assertAll(
                () -> assertTrue(interrupted.get()),
                () -> assertTrue(executor.isShutdown()),
                () -> assertTrue(executor.isTerminated())
        );
    }

    @Test
    void shouldPreserveInterruptWhenShutdownWaitIsInterrupted()
            throws Exception {
        ManagedThreadPoolExecutor executor = ThreadPoolFactory.createBounded(
                "interruptedPool",
                1,
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                "interrupted-"
        );
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch taskRelease = new CountDownLatch(1);
        AtomicBoolean closerInterruptPreserved = new AtomicBoolean();
        executor.execute(() -> {
            taskStarted.countDown();
            try {
                taskRelease.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

        Thread closer = Thread.ofPlatform().start(() -> {
            Thread.currentThread().interrupt();
            executor.shutdownGracefully();
            closerInterruptPreserved.set(
                    Thread.currentThread().isInterrupted()
            );
        });
        closer.join(1_000L);
        taskRelease.countDown();

        assertAll(
                () -> assertTrue(closerInterruptPreserved.get()),
                () -> assertTrue(executor.isShutdown())
        );
    }

    @Test
    void shouldRejectInvalidFactoryArguments() {
        Duration positive = Duration.ofSeconds(1);
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> create(null, 1, 1, 1, positive, positive, "x-")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(" ", 1, 1, 1, positive, positive, "x-")
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> create("x", 1, 1, 1, positive, positive, null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create("x", 1, 1, 1, positive, positive, " ")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create("x", 0, 1, 1, positive, positive, "x-")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create("x", 2, 1, 1, positive, positive, "x-")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create("x", 1, 1, 0, positive, positive, "x-")
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> create("x", 1, 1, 1, null, positive, "x-")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(
                                "x", 1, 1, 1,
                                Duration.ZERO, positive, "x-"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(
                                "x", 1, 1, 1,
                                Duration.ofSeconds(-1), positive, "x-"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(
                                "x", 1, 1, 1,
                                Duration.ofNanos(999_999), positive, "x-"
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> create("x", 1, 1, 1, positive, null, "x-")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(
                                "x", 1, 1, 1,
                                positive, Duration.ZERO, "x-"
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> create(
                                "x", 1, 1, 1,
                                positive, Duration.ofNanos(999_999), "x-"
                        )
                )
        );
    }

    private static ManagedThreadPoolExecutor create(
            String poolName,
            int corePoolSize,
            int maximumPoolSize,
            int queueCapacity,
            Duration keepAlive,
            Duration shutdownWait,
            String threadNamePrefix
    ) {
        return ThreadPoolFactory.createBounded(
                poolName,
                corePoolSize,
                maximumPoolSize,
                queueCapacity,
                keepAlive,
                shutdownWait,
                threadNamePrefix
        );
    }
}
