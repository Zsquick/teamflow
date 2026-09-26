package com.teamflow.scheduling;

import com.teamflow.sse.SseConnectionRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 以前一次执行结束为基准，定期维持并清理 SSE 连接。 */
@Component
public class SseHeartbeatScheduler {

    private final SseConnectionRegistry registry;

    public SseHeartbeatScheduler(SseConnectionRegistry registry) {
        this.registry = Objects.requireNonNull(
                registry,
                "SSE 连接注册表不能为 null"
        );
    }

    @Scheduled(
            fixedDelayString =
                    "${teamflow.sse.heartbeat-interval:PT20S}"
    )
    public void sendHeartbeat() {
        registry.heartbeatAll();
    }
}
