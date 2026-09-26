package com.teamflow.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用通用定时任务线程池参数。 */
@ConfigurationProperties("teamflow.scheduler")
public record SchedulerProperties(int poolSize) {

    public SchedulerProperties {
        if (poolSize < 1 || poolSize > 32) {
            throw new IllegalArgumentException(
                    "定时任务线程数必须在 1 到 32 之间"
            );
        }
    }
}
