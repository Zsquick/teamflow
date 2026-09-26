package com.teamflow.config;

import com.teamflow.concurrent.ObservableAbortPolicy;
import com.teamflow.integration.WebhookProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Java HttpClient 与 Webhook 重试调度器配置。 */
@Configuration(proxyBeanMethods = false)
public class WebhookConfig {

    @Bean
    public HttpClient webhookHttpClient(WebhookProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Bean(name = "webhookRetryScheduler")
    public TaskScheduler webhookRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("teamflow-webhook-retry-");
        scheduler.setRejectedExecutionHandler(
                new ObservableAbortPolicy("webhookRetryScheduler")
        );
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
