package com.teamflow.config;

import com.teamflow.concurrent.ObservableAbortPolicy;
import com.teamflow.scheduling.SchedulerProperties;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通用任务与 Outbox 调度线程池配置测试。 */
class SchedulingConfigTest {

    @Test
    void shouldCreateSeparatedSchedulersWithObservableRejection()
            throws Exception {
        SchedulingConfig config = new SchedulingConfig();
        ThreadPoolTaskScheduler general = assertInstanceOf(
                ThreadPoolTaskScheduler.class,
                config.taskScheduler(new SchedulerProperties(3))
        );
        ThreadPoolTaskScheduler outbox = assertInstanceOf(
                ThreadPoolTaskScheduler.class,
                config.outboxTaskScheduler()
        );
        CountDownLatch completed = new CountDownLatch(2);
        AtomicReference<String> generalThread = new AtomicReference<>();
        AtomicReference<String> outboxThread = new AtomicReference<>();
        general.initialize();
        outbox.initialize();

        try {
            general.schedule(() -> {
                generalThread.set(Thread.currentThread().getName());
                completed.countDown();
            }, Instant.now());
            outbox.schedule(() -> {
                outboxThread.set(Thread.currentThread().getName());
                completed.countDown();
            }, Instant.now());
            assertTrue(completed.await(2, TimeUnit.SECONDS));

            assertAll(
                    () -> assertEquals(
                            3,
                            general.getScheduledThreadPoolExecutor()
                                    .getCorePoolSize()
                    ),
                    () -> assertEquals(
                            1,
                            outbox.getScheduledThreadPoolExecutor()
                                    .getCorePoolSize()
                    ),
                    () -> assertTrue(
                            generalThread.get().startsWith(
                                    "teamflow-scheduler-"
                            )
                    ),
                    () -> assertTrue(
                            outboxThread.get().startsWith(
                                    "teamflow-outbox-"
                            )
                    ),
                    () -> assertInstanceOf(
                            ObservableAbortPolicy.class,
                            general.getScheduledThreadPoolExecutor()
                                    .getRejectedExecutionHandler()
                    ),
                    () -> assertInstanceOf(
                            ObservableAbortPolicy.class,
                            outbox.getScheduledThreadPoolExecutor()
                                    .getRejectedExecutionHandler()
                    ),
                    () -> assertTrue(
                            general.getScheduledThreadPoolExecutor()
                                    .getRemoveOnCancelPolicy()
                    ),
                    () -> assertTrue(
                            outbox.getScheduledThreadPoolExecutor()
                                    .getRemoveOnCancelPolicy()
                    )
            );
        } finally {
            general.destroy();
            outbox.destroy();
        }

        ObservableAbortPolicy policy = assertInstanceOf(
                ObservableAbortPolicy.class,
                general.getScheduledThreadPoolExecutor()
                        .getRejectedExecutionHandler()
        );
        assertThrows(
                RejectedExecutionException.class,
                () -> general.schedule(() -> {
                }, Instant.now())
        );
        assertEquals(1, policy.rejectedCount());
    }
}
