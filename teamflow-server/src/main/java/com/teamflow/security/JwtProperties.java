package com.teamflow.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * 经过启动期校验的 JWT 配置。
 *
 * <p>外部配置使用 Base64 文本提供密钥。本类只解码一次并保存为
 * {@link SecretKey}，不会向其他业务组件暴露原始配置文本。</p>
 */
@ConfigurationProperties(prefix = "teamflow.jwt")
public final class JwtProperties {

    private static final Duration MINIMUM_TTL = Duration.ofSeconds(1);
    private static final int HS256_MINIMUM_KEY_BYTES = 32;

    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecretKey signingKey;

    /**
     * 创建并校验 JWT 配置。
     *
     * @param issuer 令牌签发者
     * @param accessTokenTtl 访问令牌有效期
     * @param refreshTokenTtl 刷新令牌有效期
     * @param secretBase64 Base64 编码的 HMAC 密钥
     */
    public JwtProperties(
            String issuer,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            String secretBase64
    ) {
        this.issuer = requireText(issuer, "JWT 签发者");
        this.accessTokenTtl = requireValidTtl(
                accessTokenTtl,
                "访问令牌有效期"
        );
        this.refreshTokenTtl = requireValidTtl(
                refreshTokenTtl,
                "刷新令牌有效期"
        );
        if (this.refreshTokenTtl.compareTo(this.accessTokenTtl) <= 0) {
            throw new IllegalArgumentException(
                    "刷新令牌有效期必须长于访问令牌有效期"
            );
        }
        this.signingKey = decodeSigningKey(secretBase64);
    }

    /**
     * 返回令牌签发者。
     *
     * @return 去除首尾空白后的签发者
     */
    public String issuer() {
        return issuer;
    }

    /**
     * 返回访问令牌有效期。
     *
     * @return 访问令牌有效期
     */
    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * 返回刷新令牌有效期。
     *
     * @return 刷新令牌有效期
     */
    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    /**
     * 返回启动时已经构造完成的签名密钥。
     *
     * <p>仅供同一安全包中的 JWT 服务使用。</p>
     *
     * @return HMAC 签名密钥
     */
    SecretKey signingKey() {
        return signingKey;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return stripped;
    }

    private static Duration requireValidTtl(
            Duration value,
            String fieldName
    ) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        if (value.compareTo(MINIMUM_TTL) < 0) {
            throw new IllegalArgumentException(
                    fieldName + "不能短于 1 秒"
            );
        }
        return value;
    }

    private static SecretKey decodeSigningKey(String secretBase64) {
        String encodedSecret = requireText(
                secretBase64,
                "JWT HMAC 密钥"
        );

        final byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(encodedSecret);
        } catch (DecodingException exception) {
            throw new IllegalArgumentException(
                    "JWT HMAC 密钥必须是合法的 Base64 文本",
                    exception
            );
        }

        try {
            if (keyBytes.length < HS256_MINIMUM_KEY_BYTES) {
                throw new IllegalArgumentException(
                        "JWT HMAC 密钥解码后不能少于 32 字节"
                );
            }
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
}
