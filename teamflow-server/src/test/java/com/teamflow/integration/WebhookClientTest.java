package com.teamflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Webhook 原始正文签名、异步发送和有限重试策略测试。 */
class WebhookClientTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-19T12:34:56Z"),
            ZoneOffset.UTC
    );
    private static final String SECRET = "test-secret";

    private HttpClient httpClient;
    private TaskScheduler retryScheduler;
    private ObjectMapper objectMapper;
    private List<HttpRequest> requests;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        retryScheduler = mock(TaskScheduler.class);
        objectMapper = new ObjectMapper();
        requests = new ArrayList<>();
        when(retryScheduler.schedule(
                any(Runnable.class),
                any(Instant.class)
        )).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return mock(ScheduledFuture.class);
        });
    }

    @Test
    void shouldReturnPendingFutureAndSignExactTransmittedBytes()
            throws Exception {
        CompletableFuture<HttpResponse<Void>> pending =
                new CompletableFuture<>();
        enqueue(pending);
        WebhookClient client = client(true);
        SampleEvent event = new SampleEvent("evt-001", "t001");

        CompletableFuture<Void> delivery = client.sendTaskEvent(event);

        assertFalse(delivery.isDone());
        assertEquals(1, requests.size());
        HttpRequest request = requests.getFirst();
        byte[] expectedBody = objectMapper.writeValueAsBytes(event);
        assertArrayEquals(expectedBody, readBody(request));
        assertEquals(
                "sha256=" + new HmacSigner().sign(expectedBody, SECRET),
                header(request, "X-TeamFlow-Signature")
        );
        assertEquals("evt-001", header(request, "X-TeamFlow-Event-Id"));
        assertEquals(
                "2026-09-19T12:34:56.000Z",
                header(request, "X-TeamFlow-Timestamp")
        );
        assertEquals(
                Duration.ofSeconds(2),
                request.timeout().orElseThrow()
        );

        pending.complete(response(204));
        delivery.join();
    }

    @Test
    void shouldRetryNetworkFailureAndRecoverableStatus() {
        enqueue(
                CompletableFuture.failedFuture(new IOException("reset")),
                CompletableFuture.completedFuture(response(503)),
                CompletableFuture.completedFuture(response(204))
        );

        client(true).sendTaskEvent(
                new SampleEvent("evt-002", "t002")
        ).join();

        assertEquals(3, requests.size());
        ArgumentCaptor<Instant> retryTimes = ArgumentCaptor.forClass(
                Instant.class
        );
        verify(retryScheduler, times(2)).schedule(
                any(Runnable.class),
                retryTimes.capture()
        );
        assertEquals(
                List.of(
                        CLOCK.instant().plusMillis(250),
                        CLOCK.instant().plusMillis(500)
                ),
                retryTimes.getAllValues()
        );
        byte[] originalBody = readBody(requests.getFirst());
        String originalSignature = header(
                requests.getFirst(),
                "X-TeamFlow-Signature"
        );
        for (HttpRequest request : requests) {
            assertArrayEquals(originalBody, readBody(request));
            assertEquals(
                    originalSignature,
                    header(request, "X-TeamFlow-Signature")
            );
            assertEquals(
                    "evt-002",
                    header(request, "X-TeamFlow-Event-Id")
            );
        }
    }

    @Test
    void shouldNotRetryPermanentServerProtocolFailure() {
        enqueue(CompletableFuture.completedFuture(response(501)));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> client(true).sendTaskEvent(
                        new SampleEvent("evt-003", "t003")
                ).join()
        );

        assertInstanceOf(WebhookDeliveryException.class, failure.getCause());
        assertEquals(1, requests.size());
        verify(retryScheduler, never()).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void shouldNotRetryNonNetworkAsyncFailure() {
        enqueue(CompletableFuture.failedFuture(
                new IllegalStateException("client programming failure")
        ));

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> client(true).sendTaskEvent(
                        new SampleEvent("evt-non-network", "t003")
                ).join()
        );

        assertInstanceOf(WebhookDeliveryException.class, failure.getCause());
        assertInstanceOf(
                IllegalStateException.class,
                failure.getCause().getCause()
        );
        assertEquals(1, requests.size());
        verify(retryScheduler, never()).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void shouldStopAfterThreeAttempts() {
        enqueue(
                CompletableFuture.completedFuture(response(503)),
                CompletableFuture.completedFuture(response(503)),
                CompletableFuture.completedFuture(response(503))
        );

        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> client(true).sendTaskEvent(
                        new SampleEvent("evt-004", "t004")
                ).join()
        );

        assertInstanceOf(WebhookDeliveryException.class, failure.getCause());
        assertEquals(3, requests.size());
        verify(retryScheduler, times(2)).schedule(
                any(Runnable.class),
                any(Instant.class)
        );
    }

    @Test
    void shouldDoNothingWhenIntegrationIsDisabled() {
        client(false).sendTaskEvent(
                new SampleEvent("evt-005", "t005")
        ).join();

        assertEquals(0, requests.size());
        verify(httpClient, never()).sendAsync(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    private WebhookClient client(boolean enabled) {
        return new WebhookClient(
                httpClient,
                objectMapper,
                new HmacSigner(),
                new WebhookProperties(
                        enabled,
                        URI.create("https://hooks.example.com/events"),
                        enabled ? SECRET : null,
                        Duration.ofSeconds(2)
                ),
                retryScheduler,
                CLOCK
        );
    }

    @SafeVarargs
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final void enqueue(
            CompletableFuture<HttpResponse<Void>>... responses
    ) {
        Deque<CompletableFuture<HttpResponse<Void>>> queue =
                new ArrayDeque<>(Arrays.asList(responses));
        doAnswer(invocation -> {
            requests.add(invocation.getArgument(0, HttpRequest.class));
            return (CompletableFuture) queue.removeFirst();
        }).when(httpClient).sendAsync(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        );
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Void> response(int status) {
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        return response;
    }

    private String header(HttpRequest request, String name) {
        return request.headers().firstValue(name).orElseThrow();
    }

    private byte[] readBody(HttpRequest request) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(
                new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ByteBuffer item) {
                        byte[] chunk = new byte[item.remaining()];
                        item.get(chunk);
                        output.writeBytes(chunk);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        completion.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        completion.complete(null);
                    }
                }
        );
        completion.join();
        return output.toByteArray();
    }

    private record SampleEvent(String eventId, String taskId) {
    }
}
