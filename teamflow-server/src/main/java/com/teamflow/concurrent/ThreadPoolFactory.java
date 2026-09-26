package com.teamflow.concurrent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 统一创建命名明确、队列有界的业务线程池。 */
public final class ThreadPoolFactory {

    private ThreadPoolFactory() {
    }

    public static ManagedThreadPoolExecutor createBounded(
            String poolName,
            int corePoolSize,
            int maximumPoolSize,
            int queueCapacity,
            Duration keepAlive,
            Duration shutdownWait,
            String threadNamePrefix
    ) {
        String validatedPoolName = requireText(poolName, "线程池名称");
        String validatedPrefix = requireText(
                threadNamePrefix,
                "线程名前缀"
        );
        requirePoolSizes(corePoolSize, maximumPoolSize);
        requirePositive(queueCapacity, "队列容量");
        requirePositive(keepAlive, "空闲存活时间");
        requirePositive(shutdownWait, "关闭等待时间");

        return new ManagedThreadPoolExecutor(
                validatedPoolName,
                corePoolSize,
                maximumPoolSize,
                keepAlive,
                new ArrayBlockingQueue<>(queueCapacity),
                new NamedThreadFactory(validatedPrefix),
                new ObservableAbortPolicy(validatedPoolName),
                shutdownWait
        );
    }

    private static void requirePoolSizes(
            int corePoolSize,
            int maximumPoolSize
    ) {
        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("核心线程数必须大于 0");
        }
        if (maximumPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    "最大线程数不能小于核心线程数"
            );
        }
    }

    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }

    private static void requirePositive(
            Duration value,
            String fieldName
    ) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        if (value.isZero()
                || value.isNegative()
                || value.toMillis() == 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不能为 null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final ThreadFactory delegate =
                Executors.defaultThreadFactory();
        private final AtomicInteger sequence = new AtomicInteger();
        private final String prefix;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = delegate.newThread(task);
            thread.setName(prefix + sequence.incrementAndGet());
            return thread;
        }
    }
}
