package com.teamflow.core.task.service;

import com.teamflow.core.task.event.TaskAssignmentChanged;

/** 在当前业务事务中持久化待发布的任务领域事件。 */
public interface TaskEventPublisher {

    /**
     * 将任务负责人变化事件写入事务 Outbox。
     *
     * @param event 与任务变更一同提交的不可变事件快照
     */
    void publishAssignmentChanged(TaskAssignmentChanged event);
}
