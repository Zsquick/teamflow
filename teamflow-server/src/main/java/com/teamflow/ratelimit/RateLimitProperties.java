package com.teamflow.ratelimit;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP 入口固定窗口限流配置。 */
@ConfigurationProperties("teamflow.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Policy login,
        Policy registration,
        Policy refresh,
        Policy upload
) {

    public RateLimitProperties {
        login = Objects.requireNonNull(login, "登录限流配置不能为 null");
        registration = Objects.requireNonNull(
                registration,
                "注册限流配置不能为 null"
        );
        refresh = Objects.requireNonNull(refresh, "刷新限流配置不能为 null");
        upload = Objects.requireNonNull(upload, "上传限流配置不能为 null");
    }

    /** 单个限流入口允许的请求数和固定窗口长度。 */
    public record Policy(int maxRequests, Duration window) {

        public Policy {
            if (maxRequests < 1) {
                throw new IllegalArgumentException("限流请求数必须大于 0");
            }
            Objects.requireNonNull(window, "限流窗口不能为 null");
            if (window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("限流窗口必须大于 0");
            }
            long windowMillis;
            try {
                windowMillis = window.toMillis();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "限流窗口超出毫秒范围",
                        exception
                );
            }
            if (windowMillis < 1L) {
                throw new IllegalArgumentException("限流窗口不能小于 1 毫秒");
            }
            if (windowMillis > Long.MAX_VALUE / 2L) {
                throw new IllegalArgumentException("限流窗口过大，无法计算安全过期时间");
            }
        }
    }
}
