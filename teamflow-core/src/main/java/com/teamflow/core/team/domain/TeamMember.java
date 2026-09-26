package com.teamflow.core.team.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;

import java.util.Objects;

/**
 * 团队与用户之间的不可变成员关系快照。
 */
public class TeamMember {

    private final String id;
    private final String teamId;
    private final String userId;
    private final TeamRole role;
    private final String joinedAt;

    /**
     * 使用持久化数据还原团队成员关系。
     *
     * @param id 成员关系编号
     * @param teamId 团队编号
     * @param userId 用户编号
     * @param role 团队角色
     * @param joinedAt 加入时间 UTC 文本
     */
    public TeamMember(
            String id,
            String teamId,
            String userId,
            TeamRole role,
            String joinedAt
    ) {
        this.id = TextValues.requireNonBlank(id, "成员关系编号");
        this.teamId = TextValues.requireNonBlank(teamId, "团队编号");
        this.userId = TextValues.requireNonBlank(userId, "用户编号");
        this.role = Objects.requireNonNull(role, "团队角色不能为 null");
        this.joinedAt = UtcTimeText.requireValid(joinedAt, "加入时间");
    }

    /**
     * 创建新的团队成员关系。
     *
     * @param id 成员关系编号
     * @param teamId 团队编号
     * @param userId 用户编号
     * @param role 团队角色
     * @param joinedAt 加入时间 UTC 文本
     * @return 初始化完成的成员关系
     */
    public static TeamMember create(
            String id,
            String teamId,
            String userId,
            TeamRole role,
            String joinedAt
    ) {
        return new TeamMember(id, teamId, userId, role, joinedAt);
    }

    public String getId() {
        return id;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getUserId() {
        return userId;
    }

    public TeamRole getRole() {
        return role;
    }

    public String getJoinedAt() {
        return joinedAt;
    }
}
