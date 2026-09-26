package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.TeamRole;

/**
 * 团队成员响应。
 *
 * @param userId 用户标识
 * @param username 用户名
 * @param displayName 展示名称
 * @param avatarUrl 头像地址，可为 {@code null}
 * @param role 团队角色
 * @param joinedAt 加入时间 UTC 文本
 */
public record TeamMemberResponse(
        String userId,
        String username,
        String displayName,
        String avatarUrl,
        TeamRole role,
        String joinedAt
) {
}
