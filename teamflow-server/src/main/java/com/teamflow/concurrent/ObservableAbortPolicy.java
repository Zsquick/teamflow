package com.teamflow.concurrent;

import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记录过载现场并立即拒绝任务的线程池策略。
 *
 * <p>拒绝后继续抛出 {@link RejectedExecutionException}，让上层明确
 * 返回“系统繁忙”，避免任务被静默丢弃或回退到请求线程执行。</p>
 */
public final class ObservableAbortPolicy
        implements RejectedExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(
            ObservableAbortPolicy.class
    );

    private final String poolName;
    private final AtomicLong rejectedCount = new AtomicLong();

    public ObservableAbortPolicy(String poolName) {
        this.poolName = requireText(poolName, "线程池名称");
    }

    @Override
    public void rejectedExecution(
            Runnable task,
            ThreadPoolExecutor executor
    ) {
        Objects.requireNonNull(task, "被拒绝任务不能为 null");
        Objects.requireNonNull(executor, "线程池不能为 null");

        long currentRejectedCount = rejectedCount.incrementAndGet();
        String reason = executor.isShutdown() ? "shutdown" : "saturated";
        if (shouldLog(currentRejectedCount)) {
            log.warn(
                    "线程池拒绝任务 pool={} reason={} rejected={} "
                            + "active={} size={} max={} queued={} remaining={} "
                            + "completed={}",
                    poolName,
                    reason,
                    currentRejectedCount,
                    executor.getActiveCount(),
                    executor.getPoolSize(),
                    executor.getMaximumPoolSize(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity(),
                    executor.getCompletedTaskCount()
            );
        }
        throw new RejectedExecutionException(
                "线程池 " + poolName + " 已拒绝任务: " + reason
        );
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    private static boolean shouldLog(long rejectedCount) {
        return rejectedCount == 1
                || (rejectedCount & (rejectedCount - 1)) == 0;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }
}
