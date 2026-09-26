package com.teamflow.metrics;

import com.teamflow.core.task.dto.TaskResponse;
import com.teamflow.core.task.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 任务创建成功后的指标切面测试。 */
class BusinessMetricsAspectTest {

    @Test
    void shouldIncrementTaskCounterAfterSuccessfulReturn() {
        TeamFlowMetrics metrics = mock(TeamFlowMetrics.class);
        TaskService target = mock(TaskService.class);
        when(target.create(anyString(), any())).thenReturn(
                mock(TaskResponse.class)
        );
        TaskService proxy = proxy(target, metrics);

        proxy.create("u001", null);

        verify(metrics).recordTaskCreated();
    }

    @Test
    void shouldNotCountFailedCreation() {
        TeamFlowMetrics metrics = mock(TeamFlowMetrics.class);
        TaskService target = mock(TaskService.class);
        when(target.create(anyString(), any())).thenThrow(
                new IllegalStateException("failed")
        );
        TaskService proxy = proxy(target, metrics);

        assertThrows(
                IllegalStateException.class,
                () -> proxy.create("u001", null)
        );

        verify(metrics, never()).recordTaskCreated();
    }

    private TaskService proxy(TaskService target, TeamFlowMetrics metrics) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new BusinessMetricsAspect(metrics));
        return factory.getProxy();
    }
}
