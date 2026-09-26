package com.teamflow.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** JWT 启动配置测试。 */
class JwtPropertiesTest {

    private static final byte[] VALID_KEY_BYTES =
            "0123456789abcdef0123456789abcdef"
                    .getBytes(StandardCharsets.UTF_8);
    private static final String VALID_SECRET =
            Base64.getEncoder().encodeToString(VALID_KEY_BYTES);

    @Test
    void shouldBindValidateAndPrepareSigningKey() {
        MapConfigurationPropertySource source =
                new MapConfigurationPropertySource(Map.of(
                        "teamflow.jwt.issuer", " teamflow ",
                        "teamflow.jwt.access-token-ttl", "PT15M",
                        "teamflow.jwt.refresh-token-ttl", "P7D",
                        "teamflow.jwt.secret-base64", VALID_SECRET
                ));

        JwtProperties properties = new Binder(source)
                .bind("teamflow.jwt", Bindable.of(JwtProperties.class))
                .orElseThrow(() -> new AssertionError("JWT 配置绑定失败"));

        assertEquals("teamflow", properties.issuer());
        assertEquals(Duration.ofMinutes(15), properties.accessTokenTtl());
        assertEquals(Duration.ofDays(7), properties.refreshTokenTtl());
        assertEquals("HmacSHA256", properties.signingKey().getAlgorithm());
        assertArrayEquals(
                VALID_KEY_BYTES,
                properties.signingKey().getEncoded()
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRequiredValues")
    void shouldRejectMissingOrInvalidRequiredValue(
            String issuer,
            Duration accessTtl,
            Duration refreshTtl,
            String secret
    ) {
        assertThrows(
                RuntimeException.class,
                () -> new JwtProperties(
                        issuer,
                        accessTtl,
                        refreshTtl,
                        secret
                )
        );
    }

    @Test
    void shouldRequireRefreshTokenToLiveLongerThanAccessToken() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(
                        "teamflow",
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(15),
                        VALID_SECRET
                )
        );
    }

    @Test
    void shouldRejectInvalidOrWeakBase64Secret() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(
                        "teamflow",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        "not-valid-base64***"
                )
        );

        String weakSecret = Base64.getEncoder().encodeToString(
                "short-secret".getBytes(StandardCharsets.UTF_8)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(
                        "teamflow",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        weakSecret
                )
        );
    }

    private static Stream<Arguments> invalidRequiredValues() {
        return Stream.of(
                Arguments.of(
                        null,
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        VALID_SECRET
                ),
                Arguments.of(
                        "   ",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        VALID_SECRET
                ),
                Arguments.of(
                        "teamflow",
                        null,
                        Duration.ofDays(7),
                        VALID_SECRET
                ),
                Arguments.of(
                        "teamflow",
                        Duration.ZERO,
                        Duration.ofDays(7),
                        VALID_SECRET
                ),
                Arguments.of(
                        "teamflow",
                        Duration.ofMinutes(15),
                        null,
                        VALID_SECRET
                ),
                Arguments.of(
                        "teamflow",
                        Duration.ofMinutes(15),
                        Duration.ZERO,
                        VALID_SECRET
                ),
                Arguments.of(
                        "teamflow",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        null
                ),
                Arguments.of(
                        "teamflow",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7),
                        "   "
                )
        );
    }
}
