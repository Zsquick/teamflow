package com.teamflow.core.user.domain;

import java.util.Objects;

/**
 * 用户账号状态。
 */
public enum UserStatus {
    /** 账号正常，可以登录并访问系统。 */
    ACTIVE,

    /** 账号被管理员停用，必须由管理员重新启用。 */
    DISABLED,

    /** 账号因安全原因临时锁定，可以在完成解锁流程后恢复。 */
    LOCKED;

    /**
     * 判断当前状态是否允许进行身份认证。
     *
     * @return 只有正常状态返回 {@code true}
     */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }

    /**
     * 判断能否从当前状态切换到目标状态。
     *
     * <p>切换到相同状态被视为合法的幂等操作。停用账号不能直接进入锁定状态，
     * 因为停用期间不应该参与登录失败锁定流程。</p>
     *
     * @param target 目标状态
     * @return 是否允许切换
     */
    public boolean canTransitionTo(UserStatus target) {
        Objects.requireNonNull(target, "目标用户状态不能为 null");

        if (this == target) {
            return true;
        }

        return switch (this) {
            case ACTIVE -> target == DISABLED || target == LOCKED;
            case DISABLED -> target == ACTIVE;
            case LOCKED -> target == ACTIVE || target == DISABLED;
        };
    }
}
