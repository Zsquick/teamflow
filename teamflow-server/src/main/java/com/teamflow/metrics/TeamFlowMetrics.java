package com.teamflow.metrics;

import com.teamflow.sse.SseConnectionRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** 低基数业务指标的统一注册和记录入口。 */
@Component
public class TeamFlowMetrics {

    private final Counter taskCreatedCounter;
    private final Timer successfulImportTimer;
    private final Timer failedImportTimer;
    private final AtomicReference<Supplier<Number>> sseConnectionSupplier;

    public TeamFlowMetrics(
            MeterRegistry meterRegistry,
            SseConnectionRegistry sseConnectionRegistry
    ) {
        MeterRegistry registry = Objects.requireNonNull(
                meterRegistry,
                "指标注册表不能为 null"
        );
        this.taskCreatedCounter = Counter.builder("teamflow.tasks.created")
                .description("已创建任务总数")
                .register(registry);
        this.successfulImportTimer = importTimer(registry, true);
        this.failedImportTimer = importTimer(registry, false);
        this.sseConnectionSupplier = new AtomicReference<>(() -> 0);
        bindSseConnections(Objects.requireNonNull(
                sseConnectionRegistry,
                "SSE 连接注册表不能为 null"
        )::connectionCount);
        Gauge.builder(
                        "teamflow.sse.connections",
                        sseConnectionSupplier,
                        TeamFlowMetrics::readGaugeValue
                )
                .description("当前 SSE 连接数")
                .strongReference(true)
                .register(registry);
    }

    public void recordTaskCreated() {
        taskCreatedCounter.increment();
    }

    public void recordImportDuration(long milliseconds, boolean success) {
        if (milliseconds < 0) {
            throw new IllegalArgumentException("导入耗时不能为负数");
        }
        (success ? successfulImportTimer : failedImportTimer).record(
                milliseconds,
                TimeUnit.MILLISECONDS
        );
    }

    public void bindSseConnections(Supplier<Number> supplier) {
        sseConnectionSupplier.set(Objects.requireNonNull(
                supplier,
                "SSE 连接数提供器不能为 null"
        ));
    }

    private static Timer importTimer(
            MeterRegistry registry,
            boolean success
    ) {
        return Timer.builder("teamflow.import.duration")
                .description("CSV 导入任务耗时")
                .tag("success", Boolean.toString(success))
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .register(registry);
    }

    private static double readGaugeValue(
            AtomicReference<Supplier<Number>> reference
    ) {
        Number value = reference.get().get();
        return value == null ? 0 : value.doubleValue();
    }
}
