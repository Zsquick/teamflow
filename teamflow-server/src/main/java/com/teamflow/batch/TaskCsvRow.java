package com.teamflow.batch;

import com.teamflow.core.task.domain.TaskPriority;
import java.util.Objects;

/** CSV 一行经过解析后的中间模型。 */
public record TaskCsvRow(
        long rowNumber,
        String title,
        String description,
        TaskPriority priority,
        String assigneeEmail,
        String dueAt
) {
    public TaskCsvRow {
        if (rowNumber < 2) {
            throw new IllegalArgumentException("CSV 数据行号必须从 2 开始");
        }
        Objects.requireNonNull(priority, "任务优先级不能为 null");
    }
}
