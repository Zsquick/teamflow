package com.teamflow.core.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建项目请求。
 *
 * @param teamId 所属团队标识
 * @param name 项目名称
 * @param projectKey 项目短标识
 * @param description 项目描述
 */
public record CreateProjectRequest(
        @NotBlank(message = "团队编号不能为空")
        @Size(max = 32, message = "团队编号不能超过 32 个字符")
        @Pattern(regexp = "tm[0-9]{3,}", message = "团队编号格式不正确")
        String teamId,
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称不能超过 100 个字符")
        String name,
        @NotBlank(message = "项目短标识不能为空")
        @Size(min = 2, max = 16, message = "项目短标识长度必须为 2 到 16 个字符")
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)*",
                message = "项目短标识只能包含字母、数字和分隔短横线"
        )
        String projectKey,
        @Size(max = 1000, message = "项目描述不能超过 1000 个字符")
        String description
) {
}
