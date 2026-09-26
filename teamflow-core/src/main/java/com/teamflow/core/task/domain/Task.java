package com.teamflow.core.task.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;

import java.util.Objects;

/**
 * 项目任务持久化实体，维护可修改详情和乐观锁版本的一致性。
 */
public class Task {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;

    private final String id;
    private final String projectId;
    private final String reporterId;
    private final String createdAt;

    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private String assigneeId;
    private String dueAt;
    private int version;
    private String updatedAt;

    /** 使用持久化数据还原任务实体。 */
    public Task(
            String id,
            String projectId,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            String assigneeId,
            String reporterId,
            String dueAt,
            int version,
            String createdAt,
            String updatedAt
    ) {
        this.id = TextValues.requireNonBlank(id, "任务编号");
        this.projectId = TextValues.requireNonBlank(projectId, "项目编号");
        this.title = requireSizedNonBlank(
                title,
                "任务标题",
                MAX_TITLE_LENGTH
        );
        this.description = requireValidDescription(description);
        this.status = Objects.requireNonNull(status, "任务状态不能为 null");
        this.priority = Objects.requireNonNull(priority, "任务优先级不能为 null");
        this.assigneeId = TextValues.requireOptionalNonBlank(
                assigneeId,
                "负责人编号"
        );
        this.reporterId = TextValues.requireNonBlank(
                reporterId,
                "创建者编号"
        );
        this.dueAt = requireOptionalUtcTime(dueAt, "任务截止时间");
        this.version = Versions.requireNonNegative(version, "任务版本号");
        this.createdAt = UtcTimeText.requireValid(createdAt, "任务创建时间");
        this.updatedAt = UtcTimeText.requireAtOrAfter(
                updatedAt,
                this.createdAt,
                "任务修改时间",
                "创建时间"
        );
    }

    /** 创建初始状态为 TODO、版本为 0 的任务。 */
    public static Task create(
            String id,
            String projectId,
            String title,
            String description,
            TaskPriority priority,
            String assigneeId,
            String reporterId,
            String dueAt,
            String now
    ) {
        return new Task(
                id,
                projectId,
                requireSizedNonBlank(
                        title,
                        "任务标题",
                        MAX_TITLE_LENGTH
                ).strip(),
                normalizeDescription(description),
                TaskStatus.TODO,
                priority,
                TextValues.requireOptionalNonBlank(
                        assigneeId,
                        "负责人编号"
                ),
                reporterId,
                requireOptionalUtcTime(dueAt, "任务截止时间"),
                0,
                now,
                now
        );
    }

    /**
     * 修改允许变化的任务字段，并在真实变化时同时推进版本和修改时间。
     *
     * <p>状态迁移、项目状态、访问权限和负责人团队关系由服务层在调用前
     * 集中校验，本方法只执行已经通过业务校验的目标状态。</p>
     *
     * @return 至少一个业务字段发生变化时返回 {@code true}
     */
    public boolean updateDetails(
            String targetTitle,
            String targetDescription,
            TaskStatus targetStatus,
            TaskPriority targetPriority,
            String targetAssigneeId,
            String targetDueAt,
            String changedAt
    ) {
        String normalizedTitle = requireSizedNonBlank(
                targetTitle,
                "目标任务标题",
                MAX_TITLE_LENGTH
        ).strip();
        String normalizedDescription = normalizeDescription(targetDescription);
        TaskStatus validStatus = Objects.requireNonNull(
                targetStatus,
                "目标任务状态不能为 null"
        );
        TaskPriority validPriority = Objects.requireNonNull(
                targetPriority,
                "目标任务优先级不能为 null"
        );
        String validAssigneeId = TextValues.requireOptionalNonBlank(
                targetAssigneeId,
                "目标负责人编号"
        );
        String validDueAt = requireOptionalUtcTime(
                targetDueAt,
                "目标任务截止时间"
        );
        String validChangedAt = UtcTimeText.requireAtOrAfter(
                changedAt,
                updatedAt,
                "任务修改时间",
                "当前修改时间"
        );
        if (title.equals(normalizedTitle)
                && Objects.equals(description, normalizedDescription)
                && status == validStatus
                && priority == validPriority
                && Objects.equals(assigneeId, validAssigneeId)
                && Objects.equals(dueAt, validDueAt)) {
            return false;
        }

        int nextVersion = Versions.next(version, "任务版本号");

        title = normalizedTitle;
        description = normalizedDescription;
        status = validStatus;
        priority = validPriority;
        assigneeId = validAssigneeId;
        dueAt = validDueAt;
        version = nextVersion;
        updatedAt = validChangedAt;
        return true;
    }

    private static String requireSizedNonBlank(
            String value,
            String fieldName,
            int maxLength
    ) {
        String valid = TextValues.requireNonBlank(value, fieldName);
        if (valid.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过 " + maxLength + " 个字符"
            );
        }
        return valid;
    }

    private static String requireOptionalUtcTime(
            String value,
            String fieldName
    ) {
        return value == null ? null : UtcTimeText.requireValid(value, fieldName);
    }

    private static String normalizeDescription(String description) {
        return requireValidDescription(TextValues.stripToNull(description));
    }

    private static String requireValidDescription(String description) {
        if (description != null
                && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "任务描述不能超过 "
                            + MAX_DESCRIPTION_LENGTH
                            + " 个字符"
            );
        }
        return description;
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public String getDueAt() {
        return dueAt;
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
