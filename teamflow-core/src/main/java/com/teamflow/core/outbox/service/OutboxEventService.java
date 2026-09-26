package com.teamflow.core.outbox.service;

import com.teamflow.core.outbox.domain.OutboxEvent;
import com.teamflow.core.outbox.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 为调度器提供短事务的 Outbox 领取与结果回写边界。 */
@Service
public class OutboxEventService {

    private final OutboxEventMapper mapper;

    public OutboxEventService(OutboxEventMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "Outbox Mapper 不能为 null");
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findDispatchCandidates(
            String now,
            String staleBefore,
            int maxAttempts,
            int limit
    ) {
        return List.copyOf(Objects.requireNonNull(
                mapper.findDispatchCandidates(
                        now,
                        staleBefore,
                        maxAttempts,
                        limit,
                        0L
                ),
                "Outbox 候选事件查询结果不能为 null"
        ));
    }

    @Transactional
    public boolean claim(
            String id,
            String workerId,
            String now,
            String staleBefore,
            int maxAttempts
    ) {
        return mapper.claim(
                id,
                workerId,
                now,
                staleBefore,
                maxAttempts
        ) == 1;
    }

    @Transactional
    public int markExpiredClaimsFailed(
            String staleBefore,
            int maxAttempts,
            String lastError,
            String updatedAt
    ) {
        return mapper.markExpiredClaimsFailed(
                staleBefore,
                maxAttempts,
                lastError,
                updatedAt
        );
    }

    @Transactional
    public boolean markPublished(
            String id,
            String workerId,
            String publishedAt
    ) {
        return mapper.markPublished(id, workerId, publishedAt) == 1;
    }

    @Transactional
    public boolean markRetry(
            String id,
            String workerId,
            int attemptCount,
            String nextAttemptAt,
            String lastError,
            String updatedAt
    ) {
        return mapper.markRetry(
                id,
                workerId,
                attemptCount,
                nextAttemptAt,
                lastError,
                updatedAt
        ) == 1;
    }

    @Transactional
    public boolean markFailed(
            String id,
            String workerId,
            int attemptCount,
            String lastError,
            String updatedAt
    ) {
        return mapper.markFailed(
                id,
                workerId,
                attemptCount,
                lastError,
                updatedAt
        ) == 1;
    }
}
