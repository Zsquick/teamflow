package com.teamflow.core.user.domain;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;

import java.util.Objects;

/**
 * 用户持久化实体，同时维护用户状态、版本和时间的一致性。
 */
public class User {

    private final String id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final String createdAt;

    private String displayName;
    private String avatarUrl;
    private UserStatus status;
    private int version;
    private String updatedAt;

    /**
     * 使用持久化数据还原用户实体。
     *
     * <p>输入格式分别由请求 DTO、编号生成器、密码编码器和时间工具负责，
     * 此处只维护实体必须具备的字段以及字段之间的关系。</p>
     *
     * @param id 用户编号
     * @param username 规范化后的用户名
     * @param email 规范化后的邮箱
     * @param passwordHash BCrypt 密码摘要
     * @param displayName 展示名称
     * @param avatarUrl 头像地址，可为 {@code null}
     * @param status 用户状态
     * @param version 乐观锁版本
     * @param createdAt 创建时间文本
     * @param updatedAt 修改时间文本
     */
    public User(
            String id,
            String username,
            String email,
            String passwordHash,
            String displayName,
            String avatarUrl,
            UserStatus status,
            int version,
            String createdAt,
            String updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "用户编号不能为 null");
        this.username = Objects.requireNonNull(username, "用户名不能为 null");
        this.email = Objects.requireNonNull(email, "邮箱不能为 null");
        this.passwordHash = Objects.requireNonNull(
                passwordHash,
                "密码摘要不能为 null"
        );
        this.displayName = Objects.requireNonNull(
                displayName,
                "展示名称不能为 null"
        );
        this.avatarUrl = avatarUrl;
        this.status = Objects.requireNonNull(status, "用户状态不能为 null");
        this.version = Versions.requireNonNegative(version, "用户版本号");
        this.createdAt = UtcTimeText.requireValid(createdAt, "创建时间");
        this.updatedAt = UtcTimeText.requireAtOrAfter(
                updatedAt,
                this.createdAt,
                "修改时间",
                "创建时间"
        );
    }

    /**
     * 创建一个尚未设置头像的正常用户。
     *
     * @param id 用户编号
     * @param username 已通过请求校验并规范化的用户名
     * @param email 已通过请求校验并规范化的邮箱
     * @param passwordHash BCrypt 密码摘要
     * @param displayName 已通过请求校验的展示名称
     * @param now 当前 UTC 时间文本
     * @return 初始化完成的用户
     */
    public static User create(
            String id,
            String username,
            String email,
            String passwordHash,
            String displayName,
            String now
    ) {
        return new User(
                id,
                username,
                email,
                passwordHash,
                Objects.requireNonNull(displayName, "展示名称不能为 null").strip(),
                null,
                UserStatus.ACTIVE,
                0,
                now,
                now
        );
    }

    /**
     * 变更用户状态，并同步推进乐观锁版本和修改时间。
     *
     * @param targetStatus 目标状态
     * @param changedAt 状态变更时间
     * @return 状态实际发生变化时返回 {@code true}，幂等调用返回 {@code false}
     */
    public boolean changeStatus(UserStatus targetStatus, String changedAt) {
        Objects.requireNonNull(targetStatus, "目标用户状态不能为 null");
        String validChangedAt = UtcTimeText.requireAtOrAfter(
                changedAt,
                updatedAt,
                "状态变更时间",
                "当前修改时间"
        );
        if (status == targetStatus) {
            return false;
        }
        if (!status.canTransitionTo(targetStatus)) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }

        int nextVersion = Versions.next(version, "用户版本号");

        status = targetStatus;
        version = nextVersion;
        updatedAt = validChangedAt;
        return true;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UserStatus getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

}
