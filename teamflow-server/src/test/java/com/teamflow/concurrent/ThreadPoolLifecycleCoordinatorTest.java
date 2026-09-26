package com.teamflow.concurrent;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 多个业务线程池共享关闭时限的生命周期协调测试。 */
class ThreadPoolLifecycleCoordinatorTest {

    @Test
    void shouldShutdownEveryPoolBeforeWaitingAndShareOneDeadline()
            throws Exception {
        ManagedThreadPoolExecutor first = executor("firstPool");
        ManagedThreadPoolExecutor second = executor("secondPool");
        CountDownLatch workersStarted = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        AtomicInteger interruptedWorkers = new AtomicInteger();
        AtomicInteger cancelledTasks = new AtomicInteger();
        AtomicInteger completionCallbacks = new AtomicInteger();
        occupyAndQueue(
                first,
                workersStarted,
                releaseWorkers,
                interruptedWorkers,
                cancelledTasks
        );
        occupyAndQueue(
                second,
                workersStarted,
                releaseWorkers,
                interruptedWorkers,
                cancelledTasks
        );
        assertTrue(workersStarted.await(1, TimeUnit.SECONDS));
        ThreadPoolLifecycleCoordinator coordinator =
                new ThreadPoolLifecycleCoordinator(
                        List.of(first, second),
                        Duration.ofMillis(200),
                        Duration.ofMillis(200)
                );

        long startedAt = System.nanoTime();
        Thread stopper = Thread.ofPlatform().start(
                () -> coordinator.stop(completionCallbacks::incrementAndGet)
        );
        awaitWaitingState(stopper);

        assertAll(
                () -> assertTrue(first.isShutdown()),
                () -> assertTrue(second.isShutdown())
        );
        stopper.join(1_000L);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );

        try {
            assertAll(
                    () -> assertFalse(stopper.isAlive()),
                    () -> assertTrue(
                            elapsedMillis < 350L,
                            "两个线程池应共享一次优雅关闭时限"
                    ),
                    () -> assertTrue(first.isTerminated()),
                    () -> assertTrue(second.isTerminated()),
                    () -> assertTrue(interruptedWorkers.get() == 2),
                    () -> assertTrue(cancelledTasks.get() == 2),
                    () -> assertTrue(completionCallbacks.get() == 1),
                    () -> assertFalse(coordinator.isRunning()),
                    () -> assertTrue(coordinator.getPhase() > 0)
            );

            coordinator.stop(completionCallbacks::incrementAndGet);
            assertTrue(completionCallbacks.get() == 2);
        } finally {
            releaseWorkers.countDown();
            first.forceShutdown();
            second.forceShutdown();
        }
    }

    @Test
    void shouldPreserveInterruptAndStillRunCancellationAndStopCallbacks()
            throws Exception {
        ManagedThreadPoolExecutor executor = executor("interruptPool");
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicInteger interruptedWorkers = new AtomicInteger();
        AtomicInteger cancelledTasks = new AtomicInteger();
        AtomicInteger completionCallbacks = new AtomicInteger();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        occupyAndQueue(
                executor,
                workerStarted,
                releaseWorker,
                interruptedWorkers,
                cancelledTasks
        );
        assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
        ThreadPoolLifecycleCoordinator coordinator =
                new ThreadPoolLifecycleCoordinator(
                        List.of(executor),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)
                );

        Thread stopper = Thread.ofPlatform().start(() -> {
            Thread.currentThread().interrupt();
            coordinator.stop(completionCallbacks::incrementAndGet);
            interruptPreserved.set(Thread.currentThread().isInterrupted());
        });
        stopper.join(1_000L);

        try {
            assertAll(
                    () -> assertFalse(stopper.isAlive()),
                    () -> assertTrue(interruptPreserved.get()),
                    () -> assertTrue(executor.isShutdown()),
                    () -> assertTrue(cancelledTasks.get() == 1),
                    () -> assertTrue(completionCallbacks.get() == 1),
                    () -> assertFalse(coordinator.isRunning())
            );
        } finally {
            releaseWorker.countDown();
            executor.forceShutdown();
        }
    }

    @Test
    void shouldRejectInvalidLifecycleConfigurationAndNullCallback() {
        ManagedThreadPoolExecutor executor = executor("validationPool");
        Duration positive = Duration.ofMillis(1);
        try {
            assertAll(
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> new ThreadPoolLifecycleCoordinator(
                                    null,
                                    positive,
                                    positive
                            )
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new ThreadPoolLifecycleCoordinator(
                                    List.of(),
                                    positive,
                                    positive
                            )
                    ),
                    () -> assertThrows(
                            NullPointerException.class,
                            () -> new ThreadPoolLifecycleCoordinator(
                                    List.of(executor),
                                    null,
                                    positive
                            )
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new ThreadPoolLifecycleCoordinator(
                                    List.of(executor),
                                    Duration.ZERO,
                                    positive
                            )
                    ),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> new ThreadPoolLifecycleCoordinator(
                                    List.of(executor),
                                    positive,
                                    Duration.ofMillis(-1)
                            )
                    )
            );

            ThreadPoolLifecycleCoordinator coordinator =
                    new ThreadPoolLifecycleCoordinator(
                            List.of(executor),
                            positive,
                            positive
                    );
            assertThrows(
                    NullPointerException.class,
                    () -> coordinator.stop(null)
            );
            coordinator.stop();
            assertThrows(IllegalStateException.class, coordinator::start);
            assertFalse(coordinator.isRunning());
            coordinator.stop();
        } finally {
            executor.forceShutdown();
        }
    }

    private static ManagedThreadPoolExecutor executor(String name) {
        return ThreadPoolFactory.createBounded(
                name,
                1,
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                name + "-"
        );
    }

    private static void occupyAndQueue(
            ManagedThreadPoolExecutor executor,
            CountDownLatch workerStarted,
            CountDownLatch releaseWorker,
            AtomicInteger interruptedWorkers,
            AtomicInteger cancelledTasks
    ) {
        executor.execute(() -> {
            workerStarted.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException exception) {
                interruptedWorkers.incrementAndGet();
                Thread.currentThread().interrupt();
            }
        });
        executor.execute(new CancellationAwareRunnable() {
            @Override
            public void run() {
                throw new AssertionError("排队任务不应开始执行");
            }

            @Override
            public void cancelled() {
                cancelledTasks.incrementAndGet();
            }
        });
    }

    private static void awaitWaitingState(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING
                    || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("关闭线程没有进入等待阶段");
    }
}
