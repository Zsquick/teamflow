package com.teamflow.core.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建团队请求。
 *
 * @param name 团队名称
 * @param description 团队描述
 */
public record CreateTeamRequest(
        @NotBlank(message = "团队名称不能为空")
        @Size(max = 64, message = "团队名称不能超过 64 个字符")
        String name,
        @Size(max = 500) String description
) {
}
