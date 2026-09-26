package com.teamflow.sse;

import com.teamflow.common.validation.TextValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.LongFunction;

/** 管理用户的多端 SSE 连接，并隔离单连接网络故障。 */
@Component
public class SseConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(
            SseConnectionRegistry.class
    );

    private final ConcurrentHashMap<
            String,
            CopyOnWriteArraySet<SseEmitter>
            > connections = new ConcurrentHashMap<>();
    private final SseProperties properties;
    private final LongFunction<SseEmitter> emitterFactory;

    @Autowired
    public SseConnectionRegistry(SseProperties properties) {
        this(properties, timeout -> new SseEmitter(timeout));
    }

    SseConnectionRegistry(
            SseProperties properties,
            LongFunction<SseEmitter> emitterFactory
    ) {
        this.properties = Objects.requireNonNull(
                properties,
                "SSE 配置不能为 null"
        );
        this.emitterFactory = Objects.requireNonNull(
                emitterFactory,
                "SSE Emitter 工厂不能为 null"
        );
    }

    /** 为当前用户建立连接，并安装三个幂等清理回调。 */
    public SseEmitter connect(String userId) {
        String validUserId = TextValues.requireNonBlank(
                userId,
                "SSE 用户编号"
        );
        SseEmitter emitter = Objects.requireNonNull(
                emitterFactory.apply(properties.timeout().toMillis()),
                "SSE Emitter 工厂不能返回 null"
        );

        connections.compute(validUserId, (ignored, current) -> {
            CopyOnWriteArraySet<SseEmitter> target = current;
            if (target == null) {
                target = new CopyOnWriteArraySet<>();
            }
            target.add(emitter);
            return target;
        });

        Runnable cleanup = () -> remove(validUserId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("status", "connected")));
        } catch (IOException | IllegalStateException exception) {
            failConnection(validUserId, emitter, exception);
        }
        return emitter;
    }

    /** 向用户当前所有连接广播一个命名事件。 */
    public void send(String userId, String eventName, Object data) {
        send(userId, eventName, null, data);
    }

    /** 向用户广播带稳定事件编号的事件，供客户端断线后记录同步位置。 */
    public void send(
            String userId,
            String eventName,
            String eventId,
            Object data
    ) {
        String validUserId = TextValues.requireNonBlank(
                userId,
                "SSE 用户编号"
        );
        String validEventName = TextValues.requireNonBlank(
                eventName,
                "SSE 事件名称"
        );
        Objects.requireNonNull(data, "SSE 事件数据不能为 null");
        String validEventId = eventId == null
                ? null
                : TextValues.requireNonBlank(eventId, "SSE 事件编号");

        for (SseEmitter emitter : snapshot(validUserId)) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .name(validEventName);
                if (validEventId != null) {
                    event.id(validEventId);
                }
                emitter.send(event.data(data));
            } catch (IOException | IllegalStateException exception) {
                failConnection(validUserId, emitter, exception);
            }
        }
    }

    /** 给所有连接发送 SSE 注释心跳，并清理已经失效的连接。 */
    public void heartbeatAll() {
        for (Connection connection : snapshotAll()) {
            try {
                connection.emitter().send(
                        SseEmitter.event().comment("heartbeat")
                );
            } catch (IOException | IllegalStateException exception) {
                failConnection(
                        connection.userId(),
                        connection.emitter(),
                        exception
                );
            }
        }
    }

    /** 返回所有用户当前连接数，用于监控指标。 */
    public int connectionCount() {
        return connections.values().stream()
                .mapToInt(CopyOnWriteArraySet::size)
                .sum();
    }

    private List<SseEmitter> snapshot(String userId) {
        CopyOnWriteArraySet<SseEmitter> emitters = connections.get(userId);
        return emitters == null ? List.of() : List.copyOf(emitters);
    }

    private List<Connection> snapshotAll() {
        List<Connection> snapshot = new ArrayList<>();
        connections.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                snapshot.add(new Connection(userId, emitter));
            }
        });
        return List.copyOf(snapshot);
    }

    private void failConnection(
            String userId,
            SseEmitter emitter,
            Exception failure
    ) {
        remove(userId, emitter);
        try {
            emitter.completeWithError(failure);
        } catch (RuntimeException completionFailure) {
            log.debug(
                    "完成失效 SSE 连接时再次失败 userId={}",
                    userId,
                    completionFailure
            );
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        connections.computeIfPresent(userId, (ignored, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }

    private record Connection(String userId, SseEmitter emitter) {
    }
}
