package com.teamflow.metrics;

import com.teamflow.sse.SseConnectionRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 业务指标注册、动态 Gauge 和低基数标签测试。 */
class TeamFlowMetricsTest {

    @Test
    void shouldRegisterAndReuseFixedMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseConnectionRegistry connections = mock(SseConnectionRegistry.class);
        when(connections.connectionCount()).thenReturn(3);
        TeamFlowMetrics metrics = new TeamFlowMetrics(registry, connections);

        metrics.recordTaskCreated();
        metrics.recordTaskCreated();
        metrics.recordImportDuration(20, true);
        metrics.recordImportDuration(40, false);

        assertEquals(
                2.0,
                registry.get("teamflow.tasks.created").counter().count()
        );
        assertEquals(
                1,
                registry.get("teamflow.import.duration")
                        .tag("success", "true")
                        .timer()
                        .count()
        );
        assertEquals(
                1,
                registry.get("teamflow.import.duration")
                        .tag("success", "false")
                        .timer()
                        .count()
        );
        assertEquals(
                3.0,
                registry.get("teamflow.sse.connections").gauge().value()
        );
    }

    @Test
    void shouldKeepGaugeSupplierAliveAndReplaceable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseConnectionRegistry connections = mock(SseConnectionRegistry.class);
        when(connections.connectionCount()).thenReturn(1);
        TeamFlowMetrics metrics = new TeamFlowMetrics(registry, connections);
        AtomicInteger replacement = new AtomicInteger(7);

        metrics.bindSseConnections(replacement::get);
        assertEquals(
                7.0,
                registry.get("teamflow.sse.connections").gauge().value()
        );
        replacement.set(9);
        assertEquals(
                9.0,
                registry.get("teamflow.sse.connections").gauge().value()
        );
    }

    @Test
    void shouldExposeOnlyFixedLowCardinalityTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TeamFlowMetrics metrics = new TeamFlowMetrics(
                registry,
                mock(SseConnectionRegistry.class)
        );
        metrics.recordImportDuration(1, true);

        Set<String> tagKeys = registry.getMeters().stream()
                .map(Meter::getId)
                .flatMap(id -> id.getTags().stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());

        assertEquals(Set.of("success"), tagKeys);
        assertFalse(tagKeys.contains("userId"));
        assertFalse(tagKeys.contains("taskId"));
    }

    @Test
    void shouldRejectNegativeDuration() {
        TeamFlowMetrics metrics = new TeamFlowMetrics(
                new SimpleMeterRegistry(),
                mock(SseConnectionRegistry.class)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> metrics.recordImportDuration(-1, true)
        );
    }
}
