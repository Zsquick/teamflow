package com.teamflow.core.task.mapper;

import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.domain.TaskStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/** 任务持久化、乐观锁、调度扫描和统计查询接口。 */
@Mapper
public interface TaskMapper {

    int insert(Task task);

    int batchInsert(@Param("tasks") List<Task> tasks);

    Optional<Task> findById(@Param("id") String id);

    Optional<Task> findByIdForUpdate(@Param("id") String id);

    List<Task> findByProjectId(
            @Param("projectId") String projectId,
            @Param("status") TaskStatus status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    long countByProjectId(
            @Param("projectId") String projectId,
            @Param("status") TaskStatus status
    );

    int update(
            @Param("task") Task task,
            @Param("expectedVersion") int expectedVersion
    );

    int softDelete(
            @Param("id") String id,
            @Param("expectedVersion") int expectedVersion,
            @Param("deletedAt") String deletedAt
    );

    List<Task> findDueBetween(
            @Param("from") String from,
            @Param("to") String to,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    List<TaskStatusCount> countByStatus(
            @Param("projectId") String projectId
    );

    long countOverdue(
            @Param("projectId") String projectId,
            @Param("now") String now
    );

    /**
     * 按负责人统计已完成任务。
     *
     * <p>未指定负责人的已完成任务不计入；返回结果只包含完成数
     * 大于 0 的负责人，不为团队其他成员伪造零值行。</p>
     */
    List<AssigneeCompletedCount> countCompletedByAssignee(
            @Param("projectId") String projectId
    );
}
