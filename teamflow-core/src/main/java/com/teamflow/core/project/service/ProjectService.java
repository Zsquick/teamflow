package com.teamflow.core.project.service;

import com.teamflow.core.project.dto.CreateProjectRequest;
import com.teamflow.core.project.dto.ProjectResponse;
import com.teamflow.core.project.dto.UpdateProjectRequest;
import java.util.List;

/**
 * 项目业务契约。
 */
public interface ProjectService {

    /**
     * 在团队中创建项目。
     *
     * @param currentUserId 当前用户标识
     * @param request 创建请求
     * @return 新项目
     */
    ProjectResponse create(String currentUserId, CreateProjectRequest request);

    /**
     * 查询团队项目。
     *
     * @param currentUserId 当前用户标识
     * @param teamId 团队标识
     * @return 项目列表
     */
    List<ProjectResponse> listByTeam(String currentUserId, String teamId);

    /**
     * 查询项目详情。
     *
     * @param currentUserId 当前用户标识
     * @param projectId 项目标识
     * @return 项目信息
     */
    ProjectResponse get(String currentUserId, String projectId);

    /**
     * 修改项目。
     *
     * @param currentUserId 当前用户标识
     * @param projectId 项目标识
     * @param request 修改请求
     * @return 修改后项目
     */
    ProjectResponse update(
            String currentUserId,
            String projectId,
            UpdateProjectRequest request
    );
}
