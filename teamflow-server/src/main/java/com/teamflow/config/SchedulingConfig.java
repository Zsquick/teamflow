package com.teamflow.config;

import com.teamflow.concurrent.ObservableAbortPolicy;
import com.teamflow.scheduling.SchedulerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 启用 Spring Scheduler，供 SSE 心跳和后续维护任务复用。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "teamflow.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {

    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(name = "taskScheduler")
    public TaskScheduler taskScheduler(SchedulerProperties properties) {
        return scheduler(
                properties.poolSize(),
                "teamflow-scheduler-",
                "taskScheduler"
        );
    }

    @Bean(name = "outboxTaskScheduler")
    @ConditionalOnMissingBean(name = "outboxTaskScheduler")
    public TaskScheduler outboxTaskScheduler() {
        return scheduler(
                1,
                "teamflow-outbox-",
                "outboxTaskScheduler"
        );
    }

    private static ThreadPoolTaskScheduler scheduler(
            int poolSize,
            String threadNamePrefix,
            String poolName
    ) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setRejectedExecutionHandler(
                new ObservableAbortPolicy(poolName)
        );
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
