package com.teamflow.concurrent;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.List;
import java.util.Objects;

/** 将受管业务线程池注册到 Micrometer。 */
public final class ThreadPoolMetrics implements MeterBinder {

    private final List<ManagedThreadPoolExecutor> executors;

    public ThreadPoolMetrics(
            List<ManagedThreadPoolExecutor> executors
    ) {
        this.executors = List.copyOf(
                Objects.requireNonNull(executors, "线程池列表不能为 null")
        );
        if (this.executors.isEmpty()) {
            throw new IllegalArgumentException("线程池列表不能为空");
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Objects.requireNonNull(registry, "指标注册表不能为 null");
        for (ManagedThreadPoolExecutor executor : executors) {
            new ExecutorServiceMetrics(
                    executor,
                    executor.poolName(),
                    Tags.empty()
            ).bindTo(registry);
            FunctionCounter.builder(
                            "executor.rejected",
                            executor.rejectionPolicy(),
                            ObservableAbortPolicy::rejectedCount
                    )
                    .description("线程池累计拒绝的任务数")
                    .baseUnit("tasks")
                    .tag("name", executor.poolName())
                    .register(registry);
        }
    }
}
