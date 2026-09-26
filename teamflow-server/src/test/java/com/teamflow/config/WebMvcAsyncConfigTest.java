package com.teamflow.config;

import com.teamflow.concurrent.ManagedThreadPoolExecutor;
import com.teamflow.concurrent.ObservableAbortPolicy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spring MVC 异步执行器配置测试。 */
class WebMvcAsyncConfigTest {

    @Test
    void shouldConfigureBoundedExecutorAdapterAndTimeout() throws Exception {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(
                        WebMvcAsyncConfig.class
                );
        ManagedThreadPoolExecutor executor = context.getBean(
                WebMvcAsyncConfig.FILE_STREAMING_EXECUTOR,
                ManagedThreadPoolExecutor.class
        );

        try {
            InspectableAsyncSupportConfigurer asyncSupport =
                    configuredAsyncSupport(context);
            AsyncTaskExecutor adapter = asyncSupport.taskExecutor();
            String threadName = adapter.submit(
                    () -> Thread.currentThread().getName()
            ).get(1, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(8, executor.getCorePoolSize()),
                    () -> assertEquals(8, executor.getMaximumPoolSize()),
                    () -> assertEquals(
                            4,
                            executor.getQueue().remainingCapacity()
                    ),
                    () -> assertTrue(executor.allowsCoreThreadTimeOut()),
                    () -> assertTrue(threadName.startsWith("file-stream-")),
                    () -> assertInstanceOf(
                            ObservableAbortPolicy.class,
                            executor.getRejectedExecutionHandler()
                    ),
                    () -> assertNotNull(adapter),
                    () -> assertEquals(
                            5 * 60 * 1_000L,
                            asyncSupport.timeout()
                    )
            );
        } finally {
            context.close();
        }

        assertTrue(executor.isShutdown());
    }

    @Test
    void shouldTranslateSaturationToSpringTaskRejection()
            throws Exception {
        CountDownLatch workersStarted = new CountDownLatch(8);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             WebMvcAsyncConfig.class
                     )) {
            ManagedThreadPoolExecutor executor = context.getBean(
                    WebMvcAsyncConfig.FILE_STREAMING_EXECUTOR,
                    ManagedThreadPoolExecutor.class
            );
            ObservableAbortPolicy policy = assertInstanceOf(
                    ObservableAbortPolicy.class,
                    executor.getRejectedExecutionHandler()
            );
            AsyncTaskExecutor adapter = configuredAsyncSupport(context)
                    .taskExecutor();
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
                    adapter.execute(blocked);
                }
                assertTrue(workersStarted.await(1, TimeUnit.SECONDS));

                assertThrows(
                        TaskRejectedException.class,
                        () -> adapter.execute(
                                () -> rejectedTaskRan.set(true)
                        )
                );
                assertAll(
                        () -> assertEquals(1, policy.rejectedCount()),
                        () -> assertFalse(rejectedTaskRan.get())
                );
            } finally {
                release.countDown();
            }
        }
    }

    private static InspectableAsyncSupportConfigurer configuredAsyncSupport(
            AnnotationConfigApplicationContext context
    ) {
        WebMvcConfigurer webMvcConfigurer = context.getBean(
                "streamingWebMvcConfigurer",
                WebMvcConfigurer.class
        );
        InspectableAsyncSupportConfigurer asyncSupport =
                new InspectableAsyncSupportConfigurer();
        webMvcConfigurer.configureAsyncSupport(asyncSupport);
        return asyncSupport;
    }

    private static final class InspectableAsyncSupportConfigurer
            extends AsyncSupportConfigurer {

        private AsyncTaskExecutor taskExecutor() {
            return getTaskExecutor();
        }

        private Long timeout() {
            return getTimeout();
        }
    }
}
