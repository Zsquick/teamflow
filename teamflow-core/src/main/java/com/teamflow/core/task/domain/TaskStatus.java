package com.teamflow.core.task.domain;

import java.util.Objects;

/**
 * 任务处理状态。
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    /**
     * 判断当前状态能否迁移到目标状态。
     *
     * <p>允许保持当前状态；任务只能在相邻看板列之间前进或回退，防止
     * TODO 任务未经处理直接完成，也保留将已完成任务重新打开的能力。</p>
     *
     * @param targetStatus 目标状态
     * @return 允许迁移时返回 {@code true}
     */
    public boolean canTransitionTo(TaskStatus targetStatus) {
        Objects.requireNonNull(targetStatus, "目标任务状态不能为 null");
        return this == targetStatus
                || (this == TODO && targetStatus == IN_PROGRESS)
                || (this == IN_PROGRESS
                    && (targetStatus == TODO || targetStatus == DONE))
                || (this == DONE && targetStatus == IN_PROGRESS);
    }
}
