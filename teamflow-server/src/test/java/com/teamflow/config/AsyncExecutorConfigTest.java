package com.teamflow.config;

import com.teamflow.concurrent.ManagedThreadPoolExecutor;
import com.teamflow.concurrent.ObservableAbortPolicy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仪表盘与批处理专用有界线程池配置测试。 */
class AsyncExecutorConfigTest {

    @Test
    void shouldExposeSeparatedBoundedExecutors() throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                        AsyncExecutorConfig.class
                );
        ManagedThreadPoolExecutor dashboard = context.getBean(
                AsyncExecutorConfig.DASHBOARD_EXECUTOR,
                ManagedThreadPoolExecutor.class
        );
        ManagedThreadPoolExecutor batch = context.getBean(
                AsyncExecutorConfig.BATCH_LAUNCHER_EXECUTOR,
                ManagedThreadPoolExecutor.class
        );

        try {
            String dashboardThread = dashboard.submit(
                    () -> Thread.currentThread().getName()
            ).get(1, TimeUnit.SECONDS);
            String batchThread = batch.submit(
                    () -> Thread.currentThread().getName()
            ).get(1, TimeUnit.SECONDS);

            assertAll(
                    () -> assertNotSame(dashboard, batch),
                    () -> assertEquals(6, dashboard.getCorePoolSize()),
                    () -> assertEquals(6, dashboard.getMaximumPoolSize()),
                    () -> assertEquals(
                            3,
                            dashboard.getQueue().remainingCapacity()
                    ),
                    () -> assertEquals(1, batch.getCorePoolSize()),
                    () -> assertEquals(1, batch.getMaximumPoolSize()),
                    () -> assertEquals(
                            2,
                            batch.getQueue().remainingCapacity()
                    ),
                    () -> assertTrue(dashboard.allowsCoreThreadTimeOut()),
                    () -> assertTrue(batch.allowsCoreThreadTimeOut()),
                    () -> assertTrue(
                            dashboardThread.startsWith("dashboard-")
                    ),
                    () -> assertTrue(
                            batchThread.startsWith("batch-launcher-")
                    ),
                    () -> assertInstanceOf(
                            ObservableAbortPolicy.class,
                            dashboard.getRejectedExecutionHandler()
                    ),
                    () -> assertInstanceOf(
                            ObservableAbortPolicy.class,
                            batch.getRejectedExecutionHandler()
                    ),
                    () -> assertTrue(
                            dashboard.getMaximumPoolSize()
                                    + batch.getMaximumPoolSize() <= 10
                    )
            );
        } finally {
            context.close();
        }

        assertAll(
                () -> assertTrue(dashboard.isShutdown()),
                () -> assertTrue(batch.isShutdown())
        );
    }

    @Test
    void shouldRejectWithoutRunningTaskOnCallerWhenDashboardIsFull()
            throws Exception {
        CountDownLatch workersStarted = new CountDownLatch(6);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             AsyncExecutorConfig.class
                     )) {
            ManagedThreadPoolExecutor executor = context.getBean(
                    AsyncExecutorConfig.DASHBOARD_EXECUTOR,
                    ManagedThreadPoolExecutor.class
            );
            ObservableAbortPolicy rejectionPolicy = assertInstanceOf(
                    ObservableAbortPolicy.class,
                    executor.getRejectedExecutionHandler()
            );
            Runnable blocked = () -> {
                workersStarted.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            };

            try {
                int acceptedCapacity = executor.getMaximumPoolSize()
                        + executor.getQueue().remainingCapacity();
                for (int index = 0; index < acceptedCapacity; index++) {
                    executor.execute(blocked);
                }

                assertTrue(workersStarted.await(1, TimeUnit.SECONDS));
                RejectedExecutionException exception = assertThrows(
                        RejectedExecutionException.class,
                        () -> executor.execute(
                                () -> rejectedTaskRan.set(true)
                        )
                );

                assertAll(
                        () -> assertEquals(
                                executor.getMaximumPoolSize(),
                                executor.getActiveCount()
                        ),
                        () -> assertEquals(3, executor.getQueue().size()),
                        () -> assertEquals(1, rejectionPolicy.rejectedCount()),
                        () -> assertTrue(
                                exception.getMessage().contains("saturated")
                        ),
                        () -> assertFalse(rejectedTaskRan.get())
                );
            } finally {
                release.countDown();
            }
        }
    }
}
