package com.teamflow.core.project.domain;

import java.util.Objects;

/**
 * 项目生命周期状态。
 */
public enum ProjectStatus {
    ACTIVE,
    ARCHIVED;

    /**
     * 判断当前状态能否迁移到目标状态。
     *
     * <p>相同状态用于幂等更新；项目归档后不允许重新激活，避免后续任务、
     * 通知和统计功能对“已归档”产生相互矛盾的解释。</p>
     *
     * @param targetStatus 目标状态
     * @return 允许迁移或保持当前状态时返回 {@code true}
     */
    public boolean canTransitionTo(ProjectStatus targetStatus) {
        Objects.requireNonNull(targetStatus, "目标项目状态不能为 null");
        return this == targetStatus
                || (this == ACTIVE && targetStatus == ARCHIVED);
    }
}
