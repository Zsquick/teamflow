package com.teamflow.core.project.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 项目持久化实体，维护项目详情、生命周期和乐观锁版本的一致性。
 */
public class Project {

    private static final Pattern PROJECT_KEY_PATTERN = Pattern.compile(
            "[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*"
    );

    private final String id;
    private final String teamId;
    private final String projectKey;
    private final String createdBy;
    private final String createdAt;

    private String name;
    private String description;
    private ProjectStatus status;
    private int version;
    private String updatedAt;

    /**
     * 使用持久化数据还原项目实体。
     */
    public Project(
            String id,
            String teamId,
            String name,
            String projectKey,
            String description,
            ProjectStatus status,
            String createdBy,
            int version,
            String createdAt,
            String updatedAt
    ) {
        this.id = TextValues.requireNonBlank(id, "项目编号");
        this.teamId = TextValues.requireNonBlank(teamId, "团队编号");
        this.name = TextValues.requireNonBlank(name, "项目名称");
        this.projectKey = requireCanonicalProjectKey(projectKey);
        this.description = description;
        this.status = Objects.requireNonNull(status, "项目状态不能为 null");
        this.createdBy = TextValues.requireNonBlank(
                createdBy,
                "创建者用户编号"
        );
        this.version = Versions.requireNonNegative(version, "项目版本号");
        this.createdAt = UtcTimeText.requireValid(createdAt, "创建时间");
        this.updatedAt = UtcTimeText.requireAtOrAfter(
                updatedAt,
                this.createdAt,
                "修改时间",
                "创建时间"
        );
    }

    /**
     * 创建一个初始状态为 ACTIVE、版本为 0 的项目。
     */
    public static Project create(
            String id,
            String teamId,
            String name,
            String projectKey,
            String description,
            String createdBy,
            String now
    ) {
        return new Project(
                id,
                teamId,
                TextValues.requireNonBlank(name, "项目名称").strip(),
                normalizeProjectKey(projectKey),
                TextValues.stripToNull(description),
                ProjectStatus.ACTIVE,
                createdBy,
                0,
                now,
                now
        );
    }

    /**
     * 修改允许变更的项目字段，并在实际变化时同步推进版本和修改时间。
     *
     * <p>状态迁移规则由调用它的项目服务通过 {@link ProjectStatus} 校验，
     * 本方法只负责以原子方式改变已经通过业务校验的目标状态。</p>
     *
     * @return 至少一个允许修改的字段发生变化时返回 {@code true}
     */
    public boolean updateDetails(
            String targetName,
            String targetDescription,
            ProjectStatus targetStatus,
            String changedAt
    ) {
        String normalizedName = TextValues.requireNonBlank(
                targetName,
                "目标项目名称"
        ).strip();
        String normalizedDescription = TextValues.stripToNull(
                targetDescription
        );
        ProjectStatus validStatus = Objects.requireNonNull(
                targetStatus,
                "目标项目状态不能为 null"
        );
        String validChangedAt = UtcTimeText.requireAtOrAfter(
                changedAt,
                updatedAt,
                "项目修改时间",
                "当前修改时间"
        );
        if (name.equals(normalizedName)
                && Objects.equals(description, normalizedDescription)
                && status == validStatus) {
            return false;
        }

        int nextVersion = Versions.next(version, "项目版本号");

        name = normalizedName;
        description = normalizedDescription;
        status = validStatus;
        version = nextVersion;
        updatedAt = validChangedAt;
        return true;
    }

    private static String normalizeProjectKey(String projectKey) {
        return requireCanonicalProjectKey(
                TextValues.requireNonBlank(projectKey, "项目短标识")
                        .strip()
                        .toUpperCase(Locale.ROOT)
        );
    }

    private static String requireCanonicalProjectKey(String projectKey) {
        String value = TextValues.requireNonBlank(
                projectKey,
                "项目短标识"
        );
        if (value.length() < 2
                || value.length() > 16
                || !PROJECT_KEY_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "项目短标识必须为 2 到 16 个字符，以大写字母开头，且只能包含大写字母、数字和分隔短横线"
            );
        }
        return value;
    }

    public String getId() {
        return id;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getName() {
        return name;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
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
