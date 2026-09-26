package com.teamflow.config;

import com.teamflow.concurrent.ManagedThreadPoolExecutor;
import com.teamflow.concurrent.ThreadPoolLifecycleCoordinator;
import com.teamflow.concurrent.ThreadPoolMetrics;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一协调业务线程池的 Spring 关闭阶段。 */
@Configuration(proxyBeanMethods = false)
public class ThreadPoolLifecycleConfig {

    @Bean
    public MeterBinder threadPoolMetrics(
            List<ManagedThreadPoolExecutor> executors
    ) {
        return new ThreadPoolMetrics(executors);
    }

    @Bean
    public ThreadPoolLifecycleCoordinator threadPoolLifecycleCoordinator(
            List<ManagedThreadPoolExecutor> executors
    ) {
        return new ThreadPoolLifecycleCoordinator(
                executors,
                Duration.ofSeconds(10),
                Duration.ofSeconds(2)
        );
    }
}
