package com.teamflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * 浏览器跨域访问白名单配置。
 *
 * @param allowedOrigins 允许调用后端 API 的前端源地址
 */
@ConfigurationProperties(prefix = "teamflow.cors")
public record CorsProperties(List<String> allowedOrigins) {

    /**
     * 清理并冻结允许源列表，避免运行期被修改。
     */
    public CorsProperties {
        Objects.requireNonNull(allowedOrigins, "CORS 允许源不能为 null");
        allowedOrigins = allowedOrigins.stream()
                .map(origin -> Objects.requireNonNull(
                        origin,
                        "CORS 允许源元素不能为 null"
                ).strip())
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个 CORS 允许源");
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "CORS 允许源必须是明确地址，不能使用通配符"
            );
        }
    }
}
