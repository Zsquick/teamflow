package com.teamflow;

import com.teamflow.batch.TaskImportJobConfig;
import com.teamflow.integration.WebhookClient;
import com.teamflow.metrics.TeamFlowMetrics;
import com.teamflow.scheduling.DueTaskScheduler;
import com.teamflow.scheduling.TemporaryFileCleanupScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 不启动外部中间件的全应用 Bean 装配冒烟测试。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.flyway.enabled=false",
                "spring.batch.jdbc.initialize-schema=never",
                "spring.batch.job.enabled=false",
                "spring.rabbitmq.listener.simple.auto-startup=false",
                "spring.datasource.url=jdbc:mysql://127.0.0.1:1/teamflow"
                        + "?connectTimeout=100&socketTimeout=100",
                "spring.datasource.hikari.initialization-fail-timeout=-1",
                "spring.datasource.hikari.connection-timeout=250",
                "management.health.db.enabled=false",
                "management.health.redis.enabled=false",
                "management.health.rabbit.enabled=false",
                "teamflow.jwt.secret-base64="
                        + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "teamflow.storage.root=target/smoke-storage",
                "teamflow.scheduler.enabled=false",
                "teamflow.webhook.enabled=false"
        }
)
@AutoConfigureMockMvc
@Import(ApplicationContextSmokeTest.NoExternalScheduling.class)
class ApplicationContextSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    @Qualifier(TaskImportJobConfig.JOB_NAME)
    private Job importJob;

    @Autowired
    @Qualifier(TaskImportJobConfig.STEP_NAME)
    private Step importStep;

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private JobLauncher jobLauncher;

    @Test
    void shouldAssembleCrossModuleApplicationWithoutExternalConnections() {
        assertAll(
                () -> assertNotNull(context.getBean(SecurityFilterChain.class)),
                () -> assertNotNull(context.getBean(OpenAPI.class)),
                () -> assertNotNull(context.getBean(TeamFlowMetrics.class)),
                () -> assertNotNull(context.getBean(DueTaskScheduler.class)),
                () -> assertNotNull(context.getBean(
                        TemporaryFileCleanupScheduler.class
                )),
                () -> assertNotNull(context.getBean(WebhookClient.class)),
                () -> assertNotNull(
                        meterRegistry.find("executor.active")
                                .tag("name", "dashboardExecutor")
                                .gauge()
                ),
                () -> assertNotNull(
                        meterRegistry.find("executor.rejected")
                                .tag("name", "dashboardExecutor")
                                .functionCounter()
                ),
                () -> assertNotNull(importJob),
                () -> assertNotNull(importStep)
        );
    }

    @Test
    void shouldExposePublicHealthAndDocsButProtectOperationalDetails()
            throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.securitySchemes.bearerAuth.scheme"
                ).value("bearer"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NoExternalScheduling {

        /**
         * 接收 @Scheduled 注册但不真正创建线程，防止冒烟测试访问
         * Redis 或数据库。
         */
        @Bean(name = "taskScheduler")
        TaskScheduler noExternalTaskScheduler() {
            return mock(TaskScheduler.class);
        }
    }
}
