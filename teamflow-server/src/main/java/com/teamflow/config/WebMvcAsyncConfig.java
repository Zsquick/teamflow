package com.teamflow.config;

import com.teamflow.concurrent.ManagedThreadPoolExecutor;
import com.teamflow.concurrent.ThreadPoolFactory;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 为 Spring MVC 异步请求提供有界执行器。 */
@Configuration(proxyBeanMethods = false)
public class WebMvcAsyncConfig {

    public static final String FILE_STREAMING_EXECUTOR =
            "fileStreamingExecutor";

    private static final int CORE_POOL_SIZE = 8;
    private static final int MAX_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY = 4;
    private static final long REQUEST_TIMEOUT_MILLIS = 5 * 60 * 1000L;

    /** 创建限制线程数和排队数量的下载执行器。 */
    @Bean(
            name = FILE_STREAMING_EXECUTOR,
            destroyMethod = "shutdownGracefully"
    )
    public ManagedThreadPoolExecutor fileStreamingExecutor() {
        return ThreadPoolFactory.createBounded(
                FILE_STREAMING_EXECUTOR,
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                QUEUE_CAPACITY,
                Duration.ofSeconds(60),
                Duration.ofSeconds(5),
                "file-stream-"
        );
    }

    /** 将有界执行器与超时设置交给 Spring MVC 异步处理。 */
    @Bean
    public WebMvcConfigurer streamingWebMvcConfigurer(
            @Qualifier(FILE_STREAMING_EXECUTOR)
            ManagedThreadPoolExecutor fileStreamingExecutor
    ) {
        TaskExecutorAdapter taskExecutor = new TaskExecutorAdapter(
                fileStreamingExecutor
        );
        return new WebMvcConfigurer() {
            @Override
            public void configureAsyncSupport(
                    AsyncSupportConfigurer configurer
            ) {
                configurer.setTaskExecutor(taskExecutor);
                configurer.setDefaultTimeout(REQUEST_TIMEOUT_MILLIS);
            }
        };
    }
}
