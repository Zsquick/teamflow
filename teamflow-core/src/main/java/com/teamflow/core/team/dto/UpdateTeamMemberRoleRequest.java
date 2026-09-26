package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.TeamRole;
import jakarta.validation.constraints.NotNull;

/**
 * 修改团队成员角色请求。
 *
 * @param role 新团队角色
 */
public record UpdateTeamMemberRoleRequest(@NotNull TeamRole role) {
}

