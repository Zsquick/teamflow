package com.teamflow.integration;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/** 对 Webhook 原始正文计算 HMAC-SHA256，不共享非线程安全的 Mac。 */
@Component
public class HmacSigner {

    public String sign(byte[] payload, String secret) {
        Objects.requireNonNull(payload, "签名正文不能为 null");
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("HMAC 密钥不能为空");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(payload)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前 JVM 不支持 HmacSHA256",
                    exception
            );
        } catch (InvalidKeyException exception) {
            throw new IllegalArgumentException("HMAC 密钥无效", exception);
        }
    }
}
