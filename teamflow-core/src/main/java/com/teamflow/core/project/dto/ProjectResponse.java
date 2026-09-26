package com.teamflow.core.project.dto;

import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.domain.Project;

import java.util.Objects;

/**
 * 项目响应。
 *
 * @param id 项目标识
 * @param teamId 团队标识
 * @param name 项目名称
 * @param projectKey 项目短标识
 * @param description 项目描述
 * @param status 项目状态
 * @param version 乐观锁版本
 * @param createdBy 创建者用户标识
 * @param createdAt 创建时间 UTC 文本
 * @param updatedAt 修改时间 UTC 文本
 */
public record ProjectResponse(
        String id,
        String teamId,
        String name,
        String projectKey,
        String description,
        ProjectStatus status,
        String createdBy,
        int version,
        String createdAt,
        String updatedAt
) {

    /** 将项目实体转换为接口响应。 */
    public static ProjectResponse from(Project project) {
        Objects.requireNonNull(project, "项目实体不能为 null");
        return new ProjectResponse(
                project.getId(),
                project.getTeamId(),
                project.getName(),
                project.getProjectKey(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedBy(),
                project.getVersion(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
