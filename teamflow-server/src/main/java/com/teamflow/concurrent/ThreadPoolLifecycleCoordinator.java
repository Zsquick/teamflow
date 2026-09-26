package com.teamflow.concurrent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 在 Spring 销毁基础设施 Bean 前，使所有业务线程池共享同一关闭时限。
 */
public final class ThreadPoolLifecycleCoordinator
        implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(
            ThreadPoolLifecycleCoordinator.class
    );

    /* 先于普通调度器停止，但晚于 Web Server 停止接收新请求。 */
    private static final int LIFECYCLE_PHASE =
            Integer.MAX_VALUE / 2 + 1_000;

    private final List<ManagedThreadPoolExecutor> executors;
    private final Duration gracefulWait;
    private final Duration forcedWait;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ThreadPoolLifecycleCoordinator(
            List<ManagedThreadPoolExecutor> executors,
            Duration gracefulWait,
            Duration forcedWait
    ) {
        this.executors = List.copyOf(
                Objects.requireNonNull(executors, "线程池列表不能为 null")
        );
        if (this.executors.isEmpty()) {
            throw new IllegalArgumentException("线程池列表不能为空");
        }
        this.gracefulWait = requirePositive(
                gracefulWait,
                "优雅关闭时限"
        );
        this.forcedWait = requirePositive(
                forcedWait,
                "强制关闭时限"
        );
    }

    @Override
    public void start() {
        if (executors.stream().anyMatch(
                ManagedThreadPoolExecutor::isShutdown
        )) {
            throw new IllegalStateException("已关闭的线程池不能重新启动");
        }
        running.set(true);
    }

    @Override
    public void stop() {
        stop(() -> {
        });
    }

    @Override
    public void stop(Runnable callback) {
        Objects.requireNonNull(callback, "停止完成回调不能为 null");
        if (!running.compareAndSet(true, false)) {
            callback.run();
            return;
        }

        boolean interrupted = false;
        try {
            executors.forEach(ManagedThreadPoolExecutor::shutdown);
            interrupted = !awaitAllUntil(deadlineAfter(gracefulWait));

            for (ManagedThreadPoolExecutor executor : executors) {
                if (!executor.isTerminated()) {
                    int cancelled = executor.forceShutdown();
                    log.warn(
                            "线程池优雅关闭超时 pool={} cancelled={}",
                            executor.poolName(),
                            cancelled
                    );
                }
            }
            if (!interrupted) {
                interrupted = !awaitAllUntil(deadlineAfter(forcedWait));
            }
            for (ManagedThreadPoolExecutor executor : executors) {
                if (!executor.isTerminated()) {
                    log.error(
                            "线程池强制关闭后仍未终止 pool={}",
                            executor.poolName()
                    );
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return LIFECYCLE_PHASE;
    }

    private boolean awaitAllUntil(long deadlineNanos) {
        for (ManagedThreadPoolExecutor executor : executors) {
            if (executor.isTerminated()) {
                continue;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return true;
            }
            try {
                executor.awaitTermination(
                        remainingNanos,
                        TimeUnit.NANOSECONDS
                );
            } catch (InterruptedException exception) {
                return false;
            }
        }
        return true;
    }

    private static long deadlineAfter(Duration duration) {
        try {
            return Math.addExact(
                    System.nanoTime(),
                    duration.toNanos()
            );
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static Duration requirePositive(
            Duration value,
            String fieldName
    ) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
        return value;
    }
}
