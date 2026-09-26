package com.teamflow.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 一名用户多连接、回调清理和坏连接隔离测试。 */
class SseConnectionRegistryTest {

    @Test
    void shouldKeepMultipleConnectionsAndRemoveCompletedOne() {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        AtomicInteger index = new AtomicInteger();
        SseConnectionRegistry registry = registry(
                List.of(first, second),
                index
        );

        registry.connect("u001");
        registry.connect("u001");
        assertEquals(2, registry.connectionCount());

        org.mockito.ArgumentCaptor<Runnable> completion =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(first).onCompletion(completion.capture());
        completion.getValue().run();
        completion.getValue().run();

        assertEquals(1, registry.connectionCount());
    }

    @Test
    void shouldRemoveBrokenEmitterWithoutAffectingOthers()
            throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        doNothing()
                .doThrow(new IOException("closed"))
                .when(broken)
                .send(any(SseEmitter.SseEventBuilder.class));
        AtomicInteger index = new AtomicInteger();
        SseConnectionRegistry registry = registry(
                List.of(broken, healthy),
                index
        );
        registry.connect("u001");
        registry.connect("u001");

        assertDoesNotThrow(
                () -> registry.send("u001", "notification", "payload")
        );

        assertEquals(1, registry.connectionCount());
        verify(healthy, times(2))
                .send(any(SseEmitter.SseEventBuilder.class));
        verify(broken).completeWithError(any(IOException.class));
    }

    @Test
    void shouldCleanOnlyFailedConnectionDuringHeartbeat()
            throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        doNothing()
                .doThrow(new IllegalStateException("complete"))
                .when(broken)
                .send(any(SseEmitter.SseEventBuilder.class));
        AtomicInteger index = new AtomicInteger();
        SseConnectionRegistry registry = registry(
                List.of(broken),
                index
        );
        registry.connect("u001");

        registry.heartbeatAll();

        assertEquals(0, registry.connectionCount());
    }

    @Test
    void shouldNotLoseConnectionsDuringConcurrentAddAndRemove()
            throws Exception {
        int connectionTotal = 120;
        Queue<Runnable> completionCallbacks =
                new ConcurrentLinkedQueue<>();
        SseConnectionRegistry registry = new SseConnectionRegistry(
                new SseProperties(
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(20)
                ),
                ignored -> {
                    SseEmitter emitter = mock(SseEmitter.class);
                    org.mockito.Mockito.doAnswer(invocation -> {
                        completionCallbacks.add(invocation.getArgument(0));
                        return null;
                    }).when(emitter).onCompletion(any(Runnable.class));
                    return emitter;
                }
        );
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> additions = new ArrayList<>();
            for (int index = 0; index < connectionTotal; index++) {
                additions.add(executor.submit(() -> {
                    start.await();
                    registry.connect("u001");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> addition : additions) {
                addition.get();
            }

            assertEquals(connectionTotal, registry.connectionCount());
            assertEquals(connectionTotal, completionCallbacks.size());

            List<Future<?>> removals = new ArrayList<>();
            for (Runnable callback : completionCallbacks) {
                removals.add(executor.submit(callback));
            }
            for (Future<?> removal : removals) {
                removal.get();
            }

            assertEquals(0, registry.connectionCount());
        } finally {
            executor.shutdownNow();
        }
    }

    private SseConnectionRegistry registry(
            List<SseEmitter> emitters,
            AtomicInteger index
    ) {
        return new SseConnectionRegistry(
                new SseProperties(
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(20)
                ),
                ignored -> emitters.get(index.getAndIncrement())
        );
    }
}
