package com.teamflow.ratelimit;

import com.teamflow.security.SecurityErrorResponseWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 创建安全过滤器链使用的请求限流过滤器。 */
@Configuration(proxyBeanMethods = false)
public class RateLimitFilterConfig {

    @Bean
    public RequestRateLimitFilter requestRateLimitFilter(
            RequestRateLimiter rateLimiter,
            RateLimitProperties properties,
            SecurityErrorResponseWriter responseWriter
    ) {
        return new RequestRateLimitFilter(
                rateLimiter,
                properties,
                responseWriter
        );
    }

    @Bean
    public FilterRegistrationBean<RequestRateLimitFilter>
            requestRateLimitFilterRegistration(
                    RequestRateLimitFilter filter
            ) {
        FilterRegistrationBean<RequestRateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
