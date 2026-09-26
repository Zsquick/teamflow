package com.teamflow.metrics;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 在不反向污染 core 模块的前提下记录任务创建指标。 */
@Aspect
@Component
public class BusinessMetricsAspect {

    private final TeamFlowMetrics metrics;

    public BusinessMetricsAspect(TeamFlowMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "业务指标不能为 null");
    }

    @AfterReturning(
            "execution(* com.teamflow.core.task.service.TaskService.create(..))"
    )
    public void recordCreatedTask() {
        metrics.recordTaskCreated();
    }
}
