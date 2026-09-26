package com.teamflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** JWT 签发、解析与用途声明转换测试。 */
class JwtTokenServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-09T08:15:30Z");
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final byte[] KEY_BYTES =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    .getBytes(StandardCharsets.UTF_8);
    private static final String SECRET_BASE64 =
            Base64.getEncoder().encodeToString(KEY_BYTES);

    private final JwtProperties properties = new JwtProperties(
            "teamflow",
            ACCESS_TTL,
            REFRESH_TTL,
            SECRET_BASE64
    );
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtTokenService service =
            new JwtTokenService(properties, fixedClock);
    private final AuthenticatedUser user = new AuthenticatedUser(
            "u001",
            "zhou",
            new LinkedHashSet<>(List.of("ROLE_USER", "ROLE_ADMIN"))
    );

    @Test
    void shouldIssueParseAndConvertAccessToken() {
        String token = service.createAccessToken(user);

        var parsedJws = Jwts.parser()
                .verifyWith(properties.signingKey())
                .clock(() -> Date.from(NOW))
                .build()
                .parseSignedClaims(token);
        Claims claims = service.parse(token);
        AuthenticatedUser restored = service.toAuthenticatedUser(claims);

        assertEquals("HS256", parsedJws.getHeader().getAlgorithm());
        assertEquals("teamflow", claims.getIssuer());
        assertEquals("u001", claims.getSubject());
        assertEquals(Date.from(NOW), claims.getIssuedAt());
        assertEquals(
                Date.from(NOW.plus(ACCESS_TTL)),
                claims.getExpiration()
        );
        assertEquals("access", claims.get("tokenType"));
        assertEquals("zhou", claims.get("username"));
        assertEquals(
                List.of("ROLE_ADMIN", "ROLE_USER"),
                claims.get("authorities")
        );
        assertNull(claims.getId());
        assertEquals(user, restored);
    }

    @Test
    void shouldIssueMinimalRefreshTokensWithUniqueIds() {
        String firstToken = service.createRefreshToken(user);
        String secondToken = service.createRefreshToken(user);

        Claims firstClaims = service.parse(firstToken);
        Claims secondClaims = service.parse(secondToken);
        ParsedRefreshToken parsedRefresh =
                service.toRefreshToken(firstClaims);

        assertEquals("teamflow", firstClaims.getIssuer());
        assertEquals("u001", firstClaims.getSubject());
        assertEquals(Date.from(NOW), firstClaims.getIssuedAt());
        assertEquals(
                Date.from(NOW.plus(REFRESH_TTL)),
                firstClaims.getExpiration()
        );
        assertEquals("refresh", firstClaims.get("tokenType"));
        assertFalse(firstClaims.containsKey("username"));
        assertFalse(firstClaims.containsKey("authorities"));
        assertNotNull(UUID.fromString(firstClaims.getId()));
        assertNotEquals(firstClaims.getId(), secondClaims.getId());
        assertNotEquals(firstToken, secondToken);
        assertEquals(
                new ParsedRefreshToken("u001", firstClaims.getId()),
                parsedRefresh
        );
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = service.createAccessToken(user);
        JwtTokenService laterService = new JwtTokenService(
                properties,
                Clock.fixed(
                        NOW.plus(ACCESS_TTL).plusSeconds(1),
                        ZoneOffset.UTC
                )
        );

        assertThrows(
                ExpiredJwtException.class,
                () -> laterService.parse(token)
        );
    }

    @Test
    void shouldRejectTamperedSignature() {
        String token = service.createAccessToken(user);
        String[] sections = token.split("\\.");
        char replacement = sections[2].charAt(0) == 'A' ? 'B' : 'A';
        sections[2] = replacement + sections[2].substring(1);
        String tampered = String.join(".", sections);

        assertThrows(
                SignatureException.class,
                () -> service.parse(tampered)
        );
    }

    @Test
    void shouldRejectWrongIssuer() {
        String token = standardBuilder("another-service")
                .claim("tokenType", "access")
                .claim("username", "zhou")
                .claim("authorities", List.of("ROLE_USER"))
                .signWith(properties.signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThrows(
                IncorrectClaimException.class,
                () -> service.parse(token)
        );
    }

    @Test
    void shouldRejectAlgorithmOtherThanHs256() {
        String token = standardBuilder("teamflow")
                .claim("tokenType", "access")
                .claim("username", "zhou")
                .claim("authorities", List.of("ROLE_USER"))
                .signWith(properties.signingKey(), Jwts.SIG.HS512)
                .compact();

        assertThrows(JwtException.class, () -> service.parse(token));
    }

    @Test
    void shouldRejectTokenWithoutRequiredStandardTimeClaims() {
        String withoutExpiration = Jwts.builder()
                .issuer("teamflow")
                .subject("u001")
                .issuedAt(Date.from(NOW))
                .signWith(properties.signingKey(), Jwts.SIG.HS256)
                .compact();
        String withoutIssuedAt = Jwts.builder()
                .issuer("teamflow")
                .subject("u001")
                .expiration(Date.from(NOW.plus(ACCESS_TTL)))
                .signWith(properties.signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThrows(
                MalformedJwtException.class,
                () -> service.parse(withoutExpiration)
        );
        assertThrows(
                MalformedJwtException.class,
                () -> service.parse(withoutIssuedAt)
        );
    }

    @Test
    void shouldRequireExpirationAfterIssuedAt() {
        String token = Jwts.builder()
                .issuer("teamflow")
                .subject("u001")
                .issuedAt(Date.from(NOW.plus(Duration.ofMinutes(10))))
                .expiration(Date.from(NOW.plus(Duration.ofMinutes(5))))
                .signWith(properties.signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThrows(
                MalformedJwtException.class,
                () -> service.parse(token)
        );
    }

    @Test
    void shouldRejectBlankToken() {
        assertThrows(MalformedJwtException.class, () -> service.parse(null));
        assertThrows(MalformedJwtException.class, () -> service.parse("   "));
    }

    @Test
    void shouldNeverConvertRefreshTokenToAuthenticatedUser() {
        Claims claims = service.parse(service.createRefreshToken(user));

        assertThrows(
                MalformedJwtException.class,
                () -> service.toAuthenticatedUser(claims)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidAccessClaims")
    void shouldStrictlyRejectInvalidAccessClaims(
            Map<String, Object> rawClaims
    ) {
        Claims claims = claimsBackedBy(rawClaims);

        assertThrows(
                MalformedJwtException.class,
                () -> service.toAuthenticatedUser(claims)
        );
    }

    @Test
    void shouldAllowAnEmptyButPresentAuthorityCollection() {
        Map<String, Object> rawClaims = validAccessClaims();
        rawClaims.put("authorities", List.of());

        AuthenticatedUser restored = service.toAuthenticatedUser(
                Jwts.claims(rawClaims)
        );

        assertTrue(restored.authorities().isEmpty());
    }

    @Test
    void shouldRejectAccessClaimsWhenRefreshIdentityIsExpected() {
        Claims claims = service.parse(service.createAccessToken(user));

        assertThrows(
                MalformedJwtException.class,
                () -> service.toRefreshToken(claims)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRefreshClaims")
    void shouldStrictlyRejectInvalidRefreshClaims(
            Map<String, Object> rawClaims
    ) {
        Claims claims = claimsBackedBy(rawClaims);

        assertThrows(
                MalformedJwtException.class,
                () -> service.toRefreshToken(claims)
        );
    }

    private io.jsonwebtoken.JwtBuilder standardBuilder(String issuer) {
        return Jwts.builder()
                .issuer(issuer)
                .subject("u001")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(ACCESS_TTL)));
    }

    private static Stream<Map<String, Object>> invalidAccessClaims() {
        List<Map<String, Object>> invalidCases = new ArrayList<>();
        invalidCases.add(changedAccessClaim("tokenType", "refresh"));
        invalidCases.add(changedAccessClaim("tokenType", 1));
        invalidCases.add(withoutAccessClaim("tokenType"));
        invalidCases.add(changedAccessClaim("sub", "   "));
        invalidCases.add(changedAccessClaim("sub", 1));
        invalidCases.add(withoutAccessClaim("sub"));
        invalidCases.add(changedAccessClaim("username", "   "));
        invalidCases.add(changedAccessClaim("username", 1));
        invalidCases.add(withoutAccessClaim("username"));
        invalidCases.add(changedAccessClaim("authorities", "ROLE_USER"));
        invalidCases.add(changedAccessClaim(
                "authorities",
                List.of("ROLE_USER", 1)
        ));
        invalidCases.add(changedAccessClaim(
                "authorities",
                List.of("ROLE_USER", "   ")
        ));
        invalidCases.add(changedAccessClaim(
                "authorities",
                List.of("ROLE_USER", "ROLE_USER")
        ));
        invalidCases.add(withoutAccessClaim("authorities"));
        return invalidCases.stream();
    }

    private static Stream<Map<String, Object>> invalidRefreshClaims() {
        Map<String, Object> wrongType = validRefreshClaims();
        wrongType.put("tokenType", "access");
        Map<String, Object> missingSubject = validRefreshClaims();
        missingSubject.remove("sub");
        Map<String, Object> nonStringSubject = validRefreshClaims();
        nonStringSubject.put("sub", 1);
        Map<String, Object> missingId = validRefreshClaims();
        missingId.remove("jti");
        Map<String, Object> blankId = validRefreshClaims();
        blankId.put("jti", "   ");
        Map<String, Object> nonStringId = validRefreshClaims();
        nonStringId.put("jti", 1);

        return Stream.of(
                wrongType,
                missingSubject,
                nonStringSubject,
                missingId,
                blankId,
                nonStringId
        );
    }

    private static Map<String, Object> changedAccessClaim(
            String name,
            Object value
    ) {
        Map<String, Object> claims = validAccessClaims();
        claims.put(name, value);
        return claims;
    }

    private static Map<String, Object> withoutAccessClaim(String name) {
        Map<String, Object> claims = validAccessClaims();
        claims.remove(name);
        return claims;
    }

    private static Map<String, Object> validAccessClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "access");
        claims.put("sub", "u001");
        claims.put("username", "zhou");
        claims.put("authorities", List.of("ROLE_USER"));
        return claims;
    }

    private static Map<String, Object> validRefreshClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");
        claims.put("sub", "u001");
        claims.put("jti", "refresh-id");
        return claims;
    }

    private static Claims claimsBackedBy(Map<String, Object> values) {
        Claims claims = mock(Claims.class);
        when(claims.get(any())).thenAnswer(
                invocation -> values.get(invocation.getArgument(0))
        );
        return claims;
    }
}
