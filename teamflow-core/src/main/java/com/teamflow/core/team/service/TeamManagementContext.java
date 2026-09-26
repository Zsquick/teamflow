package com.teamflow.core.team.service;

import com.teamflow.core.team.domain.TeamRole;

import java.util.Objects;

/**
 * 一次成员管理权限校验得到的必要角色上下文。
 *
 * <p>组合业务可以复用已查得的操作者和目标角色，避免再次查询
 * 同一成员关系。</p>
 *
 * @param operatorRole 操作者角色
 * @param targetRole 目标成员当前角色
 */
public record TeamManagementContext(
        TeamRole operatorRole,
        TeamRole targetRole
) {

    public TeamManagementContext {
        Objects.requireNonNull(operatorRole, "操作者团队角色不能为 null");
        Objects.requireNonNull(targetRole, "目标成员团队角色不能为 null");
    }
}
