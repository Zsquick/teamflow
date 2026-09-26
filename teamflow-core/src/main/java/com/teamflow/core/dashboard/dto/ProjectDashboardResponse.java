package com.teamflow.core.dashboard.dto;

import com.teamflow.common.validation.NumberValues;
import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 项目仪表盘聚合结果。
 *
 * @param projectId 项目标识
 * @param totalTasks 任务总数
 * @param overdueTasks 逾期任务数
 * @param statusCounts 各状态任务数
 * @param memberCompletedCounts 至少完成一项任务的负责人完成数
 * @param generatedAt 本次统计快照的 UTC 生成时间
 */
public record ProjectDashboardResponse(
        String projectId,
        long totalTasks,
        long overdueTasks,
        Map<String, Long> statusCounts,
        Map<String, Long> memberCompletedCounts,
        String generatedAt
) {

    /** 校验标量并将两个统计 Map 复制为保持顺序的不可变快照。 */
    public ProjectDashboardResponse {
        projectId = TextValues.requireNonBlank(projectId, "项目编号");
        NumberValues.requireNonNegative(totalTasks, "任务总数");
        NumberValues.requireNonNegative(overdueTasks, "逾期任务数");
        statusCounts = immutableCounts(statusCounts, "状态统计");
        memberCompletedCounts = immutableCounts(
                memberCompletedCounts,
                "成员完成统计"
        );
        generatedAt = UtcTimeText.requireValid(
                generatedAt,
                "仪表盘生成时间"
        );
    }

    private static Map<String, Long> immutableCounts(
            Map<String, Long> source,
            String fieldName
    ) {
        Objects.requireNonNull(source, fieldName + "不能为 null");
        LinkedHashMap<String, Long> copy = new LinkedHashMap<>();
        source.forEach((key, count) -> {
            String validKey = TextValues.requireNonBlank(
                    key,
                    fieldName + "键"
            );
            long validCount = Objects.requireNonNull(
                    count,
                    fieldName + "数量不能为 null"
            );
            NumberValues.requireNonNegative(validCount, fieldName + "数量");
            copy.put(validKey, validCount);
        });
        return Collections.unmodifiableMap(copy);
    }
}
