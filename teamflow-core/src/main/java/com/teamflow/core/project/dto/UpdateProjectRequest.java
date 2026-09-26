package com.teamflow.core.project.dto;

import com.teamflow.core.project.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 修改项目请求。
 *
 * @param name 项目名称
 * @param description 项目描述
 * @param status 项目状态
 * @param version 当前乐观锁版本
 */
public record UpdateProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称不能超过 100 个字符")
        String name,
        @Size(max = 1000, message = "项目描述不能超过 1000 个字符")
        String description,
        @NotNull ProjectStatus status,
        @NotNull @PositiveOrZero Integer version
) {
}
