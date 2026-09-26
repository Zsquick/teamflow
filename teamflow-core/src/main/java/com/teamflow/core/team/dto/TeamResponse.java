package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.Team;
import com.teamflow.core.team.domain.TeamRole;

import java.util.Objects;

/**
 * 团队响应。
 *
 * @param id 团队标识
 * @param name 团队名称
 * @param description 团队描述
 * @param ownerId 所有者标识
 * @param version 当前乐观锁版本
 * @param createdAt 创建时间 UTC 文本
 * @param updatedAt 修改时间 UTC 文本
 * @param currentUserRole 当前用户在团队中的角色
 */
public record TeamResponse(
        String id,
        String name,
        String description,
        String ownerId,
        int version,
        String createdAt,
        String updatedAt,
        TeamRole currentUserRole
) {

    /**
     * 将团队实体与当前用户角色组合成接口响应。
     *
     * @param team 团队实体
     * @param currentUserRole 当前用户的团队角色
     * @return 团队响应
     */
    public static TeamResponse from(Team team, TeamRole currentUserRole) {
        Objects.requireNonNull(team, "团队实体不能为 null");
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getOwnerId(),
                team.getVersion(),
                team.getCreatedAt(),
                team.getUpdatedAt(),
                Objects.requireNonNull(
                        currentUserRole,
                        "当前用户团队角色不能为 null"
                )
        );
    }
}
