package com.teamflow.core.team.domain;

import java.util.Objects;

/**
 * 团队成员角色及其显式权限层级。
 *
 * <p>权限判断不使用枚举的 {@link #ordinal()}，因为调整枚举声明顺序
 * 不应该改变业务权限。</p>
 */
public enum TeamRole {
    OWNER(300),
    ADMIN(200),
    MEMBER(100);

    private final int authorityLevel;

    TeamRole(int authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    /**
     * 判断当前角色是否至少达到指定权限等级。
     *
     * @param minimumRole 最低角色
     * @return 权限等级足够时返回 {@code true}
     */
    public boolean isAtLeast(TeamRole minimumRole) {
        Objects.requireNonNull(minimumRole, "最低团队角色不能为 null");
        return authorityLevel >= minimumRole.authorityLevel;
    }

    /**
     * 判断当前角色是否能管理目标角色。
     *
     * <p>只允许严格更高的角色管理更低的角色：OWNER 可管理
     * ADMIN/MEMBER，ADMIN 只可管理 MEMBER。</p>
     *
     * @param targetRole 目标成员角色
     * @return 允许管理时返回 {@code true}
     */
    public boolean canManage(TeamRole targetRole) {
        Objects.requireNonNull(targetRole, "目标团队角色不能为 null");
        return authorityLevel > targetRole.authorityLevel;
    }

    /**
     * 判断当前角色是否能授予指定角色。
     *
     * <p>OWNER 不能通过普通成员管理接口授予，其他角色只能由
     * 严格更高的角色授予。</p>
     *
     * @param targetRole 拟授予的角色
     * @return 允许授予时返回 {@code true}
     */
    public boolean canAssign(TeamRole targetRole) {
        Objects.requireNonNull(targetRole, "待授予团队角色不能为 null");
        return targetRole != OWNER && canManage(targetRole);
    }
}
