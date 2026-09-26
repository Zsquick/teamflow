package com.teamflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 负责签发并验证 TeamFlow 的访问令牌和刷新令牌。
 *
 * <p>通用解析只验证签名、算法、签发者和标准时间声明；随后必须调用
 * {@link #toAuthenticatedUser(Claims)} 或 {@link #toRefreshToken(Claims)}，
 * 按令牌用途严格读取私有声明。</p>
 */
@Service
public final class JwtTokenService {

    static final String TOKEN_TYPE_CLAIM = "tokenType";
    static final String USERNAME_CLAIM = "username";
    static final String AUTHORITIES_CLAIM = "authorities";

    private final JwtProperties properties;
    private final Clock clock;
    private final JwtParser parser;

    /**
     * 创建 JWT 服务，并构造可供所有请求线程复用的不可变解析器。
     *
     * @param properties 已在启动阶段校验的 JWT 配置
     * @param clock 项目统一时钟
     */
    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = Objects.requireNonNull(
                properties,
                "JWT 配置不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "项目时钟不能为 null");
        this.parser = Jwts.parser()
                .verifyWith(properties.signingKey())
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(this.clock.instant()))
                .sig()
                .remove(Jwts.SIG.HS384)
                .remove(Jwts.SIG.HS512)
                .remove(Jwts.SIG.RS256)
                .remove(Jwts.SIG.RS384)
                .remove(Jwts.SIG.RS512)
                .remove(Jwts.SIG.PS256)
                .remove(Jwts.SIG.PS384)
                .remove(Jwts.SIG.PS512)
                .remove(Jwts.SIG.ES256)
                .remove(Jwts.SIG.ES384)
                .remove(Jwts.SIG.ES512)
                .remove(Jwts.SIG.EdDSA)
                .and()
                .build();
    }

    /**
     * 创建短期访问令牌。
     *
     * <p>权限在写入前按名称排序，使相同身份在同一签发时刻产生稳定的
     * JSON 声明顺序。JWT 中只保存鉴权所需的最小用户信息。</p>
     *
     * @param user 当前认证用户
     * @return 使用 HS256 签名的访问令牌
     */
    public String createAccessToken(AuthenticatedUser user) {
        Objects.requireNonNull(user, "认证用户不能为 null");

        List<String> sortedAuthorities = user.authorities()
                .stream()
                .sorted()
                .toList();
        Instant issuedAt = clock.instant();

        return createTokenBuilder(
                user.id(),
                issuedAt,
                properties.accessTokenTtl(),
                JwtTokenType.ACCESS
        )
                .claim(USERNAME_CLAIM, user.username())
                .claim(AUTHORITIES_CLAIM, sortedAuthorities)
                .compact();
    }

    /**
     * 创建长期刷新令牌。
     *
     * <p>刷新令牌仅保存用户编号、令牌类型和随机 jti，不携带用户名与
     * 权限。刷新时必须重新读取数据库，以获得最新用户状态。</p>
     *
     * @param user 当前认证用户
     * @return 使用 HS256 签名的刷新令牌
     */
    public String createRefreshToken(AuthenticatedUser user) {
        Objects.requireNonNull(user, "认证用户不能为 null");

        Instant issuedAt = clock.instant();

        return createTokenBuilder(
                user.id(),
                issuedAt,
                properties.refreshTokenTtl(),
                JwtTokenType.REFRESH
        )
                .id(UUID.randomUUID().toString())
                .compact();
    }

    /**
     * 验证 JWT 的 HS256 签名、签发者和有效期并解析声明。
     *
     * <p>该方法不根据调用场景猜测令牌用途。调用方解析后还必须选择访问
     * 身份或刷新身份转换方法，以校验 tokenType 和用途专属声明。</p>
     *
     * @param token 紧凑格式 JWT
     * @return 经过密码学和标准声明验证的声明
     * @throws io.jsonwebtoken.JwtException 令牌格式、签名、算法、签发者或
     *                                      有效期不合法
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("JWT 不能为空");
        }

        Claims claims = parser.parseSignedClaims(token).getPayload();
        requireStandardTimeClaims(claims);
        return claims;
    }

    /**
     * 将已验证的访问令牌声明转换为不含凭据的认证身份。
     *
     * @param claims {@link #parse(String)} 返回的声明
     * @return 可放入 Spring Security 上下文的用户身份
     * @throws MalformedJwtException 令牌类型或访问令牌专属声明不合法
     */
    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        requireTokenType(claims, JwtTokenType.ACCESS);

        String userId = requireTextClaim(
                claims,
                Claims.SUBJECT,
                "访问令牌缺少合法 subject"
        );
        String username = requireTextClaim(
                claims,
                USERNAME_CLAIM,
                "访问令牌缺少合法 username"
        );
        Set<String> authorities = requireAuthorities(claims);

        return new AuthenticatedUser(userId, username, authorities);
    }

    /**
     * 将已验证的刷新令牌声明转换为刷新业务所需的最小身份。
     *
     * @param claims {@link #parse(String)} 返回的声明
     * @return 用户编号与刷新令牌唯一编号
     * @throws MalformedJwtException 令牌类型、subject 或 jti 不合法
     */
    public ParsedRefreshToken toRefreshToken(Claims claims) {
        requireTokenType(claims, JwtTokenType.REFRESH);

        String userId = requireTextClaim(
                claims,
                Claims.SUBJECT,
                "刷新令牌缺少合法 subject"
        );
        String tokenId = requireTextClaim(
                claims,
                Claims.ID,
                "刷新令牌缺少合法 jti"
        );

        return new ParsedRefreshToken(userId, tokenId);
    }

    private io.jsonwebtoken.JwtBuilder createTokenBuilder(
            String userId,
            Instant issuedAt,
            Duration ttl,
            JwtTokenType tokenType
    ) {
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(userId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .claim(TOKEN_TYPE_CLAIM, tokenType.claimValue())
                .signWith(properties.signingKey(), Jwts.SIG.HS256);
    }

    private static void requireTokenType(
            Claims claims,
            JwtTokenType expectedType
    ) {
        if (claims == null) {
            throw new MalformedJwtException("JWT 声明不能为 null");
        }

        Object actualType = claims.get(TOKEN_TYPE_CLAIM);
        if (!(actualType instanceof String value)
                || !expectedType.claimValue().equals(value)) {
            throw new MalformedJwtException(
                    "JWT tokenType 不是 " + expectedType.claimValue()
            );
        }
    }

    private static void requireStandardTimeClaims(Claims claims) {
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        if (issuedAt == null || expiration == null) {
            throw new MalformedJwtException(
                    "JWT 必须包含 iat 和 exp"
            );
        }
        if (!expiration.after(issuedAt)) {
            throw new MalformedJwtException(
                    "JWT exp 必须晚于 iat"
            );
        }
    }

    private static String requireTextClaim(
            Claims claims,
            String claimName,
            String errorMessage
    ) {
        Object rawValue = claims.get(claimName);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new MalformedJwtException(errorMessage);
        }
        return value;
    }

    private static Set<String> requireAuthorities(Claims claims) {
        Object rawAuthorities = claims.get(AUTHORITIES_CLAIM);
        if (!(rawAuthorities instanceof Collection<?> values)) {
            throw new MalformedJwtException(
                    "访问令牌缺少合法 authorities"
            );
        }

        Set<String> authorities = new LinkedHashSet<>();
        for (Object rawAuthority : values) {
            if (!(rawAuthority instanceof String authority)
                    || authority.isBlank()) {
                throw new MalformedJwtException(
                        "访问令牌 authorities 只能包含非空字符串"
                );
            }
            if (!authorities.add(authority)) {
                throw new MalformedJwtException(
                        "访问令牌 authorities 不能包含重复值"
                );
            }
        }
        return Set.copyOf(authorities);
    }
}
