package com.teamflow.config;

import com.teamflow.concurrent.ManagedThreadPoolExecutor;
import com.teamflow.concurrent.ThreadPoolFactory;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为仪表盘查询和批处理启动提供相互隔离的有界执行器。
 *
 * <p>两个执行器最大合计 7 个工作线程，低于默认 Hikari 10 连接上限，
 * 为请求线程和其他业务保留连接余量。队列满后明确拒绝，交由业务层转换为
 * 可观测的稳定失败，不使用无界队列隐藏过载。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AsyncExecutorConfig {

    public static final String DASHBOARD_EXECUTOR = "dashboardExecutor";
    public static final String BATCH_LAUNCHER_EXECUTOR = "batchLauncherExecutor";

    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

    /** 创建三路统计专用、可识别且有界的执行器。 */
    @Bean(
            name = DASHBOARD_EXECUTOR,
            destroyMethod = "shutdownGracefully"
    )
    public ManagedThreadPoolExecutor dashboardExecutor() {
        return ThreadPoolFactory.createBounded(
                DASHBOARD_EXECUTOR,
                6,
                6,
                3,
                Duration.ofSeconds(30),
                SHUTDOWN_WAIT,
                "dashboard-"
        );
    }

    /** 创建独立的批处理启动执行器，避免长任务饿死仪表盘。 */
    @Bean(
            name = BATCH_LAUNCHER_EXECUTOR,
            destroyMethod = "shutdownGracefully"
    )
    public ManagedThreadPoolExecutor batchLauncherExecutor() {
        return ThreadPoolFactory.createBounded(
                BATCH_LAUNCHER_EXECUTOR,
                1,
                1,
                2,
                Duration.ofSeconds(60),
                SHUTDOWN_WAIT,
                "batch-launcher-"
        );
    }
}
