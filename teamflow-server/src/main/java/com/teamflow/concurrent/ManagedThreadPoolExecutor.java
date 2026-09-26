package com.teamflow.concurrent;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 由 Spring 托管生命周期、支持有界等待关闭的 ThreadPoolExecutor。 */
public final class ManagedThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger log = LoggerFactory.getLogger(
            ManagedThreadPoolExecutor.class
    );
    private final String poolName;
    private final ObservableAbortPolicy rejectionPolicy;
    private final Duration shutdownWait;

    ManagedThreadPoolExecutor(
            String poolName,
            int corePoolSize,
            int maximumPoolSize,
            Duration keepAlive,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            ObservableAbortPolicy rejectionPolicy,
            Duration shutdownWait
    ) {
        super(
                corePoolSize,
                maximumPoolSize,
                keepAlive.toMillis(),
                TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                rejectionPolicy
        );
        this.poolName = poolName;
        this.rejectionPolicy = rejectionPolicy;
        this.shutdownWait = shutdownWait;
        allowCoreThreadTimeOut(true);
    }

    /**
     * 先停止接收新任务并等待在途任务，超时后再中断剩余任务。
     */
    public void shutdownGracefully() {
        shutdown();
        boolean interrupted = false;
        try {
            if (awaitTermination(
                    shutdownWait.toMillis(),
                    TimeUnit.MILLISECONDS
            )) {
                return;
            }
            int cancelledTasks = forceShutdown();
            log.warn(
                    "线程池优雅关闭超时 pool={} cancelled={}",
                    poolName,
                    cancelledTasks
            );
            if (!awaitTermination(
                    1,
                    TimeUnit.SECONDS
            )) {
                log.error("线程池强制关闭后仍未终止 pool={}", poolName);
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            forceShutdown();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    String poolName() {
        return poolName;
    }

    ObservableAbortPolicy rejectionPolicy() {
        return rejectionPolicy;
    }

    int forceShutdown() {
        java.util.List<Runnable> cancelledTasks = shutdownNow();
        for (Runnable task : cancelledTasks) {
            if (task instanceof CancellationAwareRunnable awareTask) {
                try {
                    awareTask.cancelled();
                } catch (RuntimeException exception) {
                    log.error(
                            "线程池取消回调执行失败 pool={}",
                            poolName,
                            exception
                    );
                }
            }
        }
        return cancelledTasks.size();
    }
}
