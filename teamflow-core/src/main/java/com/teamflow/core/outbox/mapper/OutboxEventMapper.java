package com.teamflow.core.outbox.mapper;

import com.teamflow.core.outbox.domain.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Outbox 事件写入、领取与状态更新的数据访问接口。 */
@Mapper
public interface OutboxEventMapper {

    int insert(OutboxEvent event);

    List<OutboxEvent> findDispatchCandidates(
            @Param("now") String now,
            @Param("staleBefore") String staleBefore,
            @Param("maxAttempts") int maxAttempts,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    int claim(
            @Param("id") String id,
            @Param("workerId") String workerId,
            @Param("now") String now,
            @Param("staleBefore") String staleBefore,
            @Param("maxAttempts") int maxAttempts
    );

    int markExpiredClaimsFailed(
            @Param("staleBefore") String staleBefore,
            @Param("maxAttempts") int maxAttempts,
            @Param("lastError") String lastError,
            @Param("updatedAt") String updatedAt
    );

    int markPublished(
            @Param("id") String id,
            @Param("workerId") String workerId,
            @Param("publishedAt") String publishedAt
    );

    int markRetry(
            @Param("id") String id,
            @Param("workerId") String workerId,
            @Param("attemptCount") int attemptCount,
            @Param("nextAttemptAt") String nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") String updatedAt
    );

    int markFailed(
            @Param("id") String id,
            @Param("workerId") String workerId,
            @Param("attemptCount") int attemptCount,
            @Param("lastError") String lastError,
            @Param("updatedAt") String updatedAt
    );
}
