package com.teamflow.integration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** HMAC-SHA256 已知向量与并发安全测试。 */
class HmacSignerTest {

    private static final byte[] PAYLOAD = (
            "The quick brown fox jumps over the lazy dog"
    ).getBytes(StandardCharsets.UTF_8);
    private static final String EXPECTED =
            "97yD9DBThCSxMpjmqm+xQ+9NWaFJRhdZl0edvC0aPNg=";

    @Test
    void shouldMatchKnownHmacSha256Vector() {
        assertEquals(EXPECTED, new HmacSigner().sign(PAYLOAD, "key"));
    }

    @Test
    void shouldCreateIndependentMacForConcurrentCalls() throws Exception {
        HmacSigner signer = new HmacSigner();
        Callable<String> operation = () -> signer.sign(PAYLOAD, "key");
        List<Callable<String>> operations = java.util.stream.IntStream
                .range(0, 100)
                .mapToObj(ignored -> operation)
                .toList();

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(operations)) {
                assertEquals(EXPECTED, result.get());
            }
        }
    }

    @Test
    void shouldRejectMissingInputs() {
        HmacSigner signer = new HmacSigner();
        assertThrows(
                NullPointerException.class,
                () -> signer.sign(null, "key")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> signer.sign(PAYLOAD, "  ")
        );
    }
}
