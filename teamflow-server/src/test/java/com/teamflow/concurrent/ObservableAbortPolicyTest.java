package com.teamflow.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 可观测快速失败拒绝策略测试。 */
class ObservableAbortPolicyTest {

    @Test
    void shouldRejectSaturatedAndShutdownExecutorsWithoutRunningTask()
            throws Exception {
        ObservableAbortPolicy policy = new ObservableAbortPolicy(
                "testPool"
        );
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                policy
        );
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();
        Runnable blocked = () -> {
            workerStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            executor.execute(blocked);
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
            executor.execute(blocked);

            RejectedExecutionException first = assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(
                            () -> rejectedTaskRan.set(true)
                    )
            );
            assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(() -> {
                    })
            );
            assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(() -> {
                    })
            );

            executor.shutdown();
            RejectedExecutionException shutdown = assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(() -> {
                    })
            );

            assertAll(
                    () -> assertTrue(
                            first.getMessage().contains("saturated")
                    ),
                    () -> assertTrue(
                            shutdown.getMessage().contains("shutdown")
                    ),
                    () -> assertEquals(4, policy.rejectedCount()),
                    () -> assertFalse(rejectedTaskRan.get())
            );
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldRejectInvalidConstructionAndInvocationArguments() {
        ObservableAbortPolicy policy = new ObservableAbortPolicy("test");
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1)
        );
        try {
            assertAll(
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> new ObservableAbortPolicy(null)
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new ObservableAbortPolicy("  ")
                    ),
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> policy.rejectedExecution(null, executor)
                    ),
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> policy.rejectedExecution(() -> {
                            }, null)
                    )
            );
            assertEquals(0, policy.rejectedCount());
        } finally {
            executor.shutdownNow();
        }
    }
}
