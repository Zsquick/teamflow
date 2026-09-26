package com.teamflow.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;

/** 为只存字符串的缓存和安全令牌提供明确的 Redis 模板。 */
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(Objects.requireNonNull(
                connectionFactory,
                "Redis 连接工厂不能为 null"
        ));
    }
}
