package com.teamflow.batch;

import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.mapper.TaskMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/** 在 Spring Batch 当前 chunk 事务中执行一次 MyBatis 批量写入。 */
public class TaskCsvWriter implements ItemWriter<Task> {

    private final TaskMapper taskMapper;

    public TaskCsvWriter(TaskMapper taskMapper) {
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "任务 Mapper 不能为 null"
        );
    }

    @Override
    public void write(Chunk<? extends Task> chunk) {
        Objects.requireNonNull(chunk, "任务 chunk 不能为 null");
        if (chunk.isEmpty()) {
            return;
        }
        List<Task> tasks = List.copyOf(chunk.getItems());
        int affectedRows = taskMapper.batchInsert(tasks);
        if (affectedRows != tasks.size()) {
            throw new IllegalStateException(
                    "批量写入任务数量不一致，期望 "
                            + tasks.size()
                            + "，实际 "
                            + affectedRows
            );
        }
    }
}
