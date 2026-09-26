package com.teamflow.core.dashboard.service;

import com.teamflow.core.dashboard.dto.ProjectDashboardResponse;

/**
 * 项目统计仪表盘业务契约。
 */
public interface DashboardService {

    /**
     * 并行查询并聚合项目统计信息。
     *
     * @param currentUserId 当前用户标识
     * @param projectId 项目标识
     * @return 仪表盘数据
     */
    ProjectDashboardResponse getProjectDashboard(
            String currentUserId,
            String projectId
    );
}
