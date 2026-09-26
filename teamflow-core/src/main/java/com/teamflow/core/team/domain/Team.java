package com.teamflow.core.team.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;

import java.util.Objects;

/**
 * 团队持久化实体，维护详情、乐观锁版本和时间的一致性。
 */
public class Team {

    private final String id;
    private final String ownerId;
    private final String createdAt;

    private String name;
    private String description;
    private int version;
    private String updatedAt;

    /**
     * 使用持久化数据还原团队实体。
     *
     * @param id 团队编号
     * @param name 团队名称
     * @param description 团队描述，可为 {@code null}
     * @param ownerId 所有者用户编号
     * @param version 乐观锁版本
     * @param createdAt 创建时间 UTC 文本
     * @param updatedAt 修改时间 UTC 文本
     */
    public Team(
            String id,
            String name,
            String description,
            String ownerId,
            int version,
            String createdAt,
            String updatedAt
    ) {
        this.id = TextValues.requireNonBlank(id, "团队编号");
        this.name = TextValues.requireNonBlank(name, "团队名称");
        this.description = description;
        this.ownerId = TextValues.requireNonBlank(
                ownerId,
                "所有者用户编号"
        );
        this.version = Versions.requireNonNegative(version, "团队版本号");
        this.createdAt = UtcTimeText.requireValid(createdAt, "创建时间");
        this.updatedAt = UtcTimeText.requireAtOrAfter(
                updatedAt,
                this.createdAt,
                "修改时间",
                "创建时间"
        );
    }

    /**
     * 创建版本为 0 的新团队。
     *
     * @param id 团队编号
     * @param name 已通过请求边界校验的团队名称
     * @param description 团队描述，可为 {@code null}
     * @param ownerId 所有者用户编号
     * @param now 当前 UTC 时间文本
     * @return 初始化完成的团队
     */
    public static Team create(
            String id,
            String name,
            String description,
            String ownerId,
            String now
    ) {
        return new Team(
                id,
                TextValues.requireNonBlank(name, "团队名称").strip(),
                TextValues.stripToNull(description),
                ownerId,
                0,
                now,
                now
        );
    }

    /**
     * 修改团队名称和描述，并在实际发生变化时推进版本号。
     *
     * @param targetName 目标团队名称
     * @param targetDescription 目标团队描述，可为 {@code null}
     * @param changedAt 修改时间 UTC 文本
     * @return 详情实际发生变化时返回 {@code true}
     */
    public boolean updateDetails(
            String targetName,
            String targetDescription,
            String changedAt
    ) {
        String normalizedName = TextValues.requireNonBlank(
                targetName,
                "目标团队名称"
        ).strip();
        String normalizedDescription = TextValues.stripToNull(
                targetDescription
        );
        String validChangedAt = UtcTimeText.requireAtOrAfter(
                changedAt,
                updatedAt,
                "详情修改时间",
                "当前修改时间"
        );
        if (name.equals(normalizedName)
                && Objects.equals(description, normalizedDescription)) {
            return false;
        }

        int nextVersion = Versions.next(version, "团队版本号");

        name = normalizedName;
        description = normalizedDescription;
        version = nextVersion;
        updatedAt = validChangedAt;
        return true;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwnerId() {
        return ownerId;
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
