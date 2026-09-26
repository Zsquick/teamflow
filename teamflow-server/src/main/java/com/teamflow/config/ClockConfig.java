package com.teamflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 提供项目统一时钟。 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * 创建使用 UTC 时区的系统时钟。
     *
     * @return UTC 系统时钟
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
