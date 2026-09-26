package com.teamflow.core.importjob.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;

import java.util.Objects;

/**
 * CSV 批量导入任务持久化实体。
 */
public class ImportJob {
    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;
    private static final int MAX_STORAGE_PATH_LENGTH = 512;

    private final String id;
    private final String projectId;
    private final String creatorId;
    private final String originalFileName;
    private final String storagePath;
    private final String createdAt;

    private ImportJobStatus status;
    private long totalRows;
    private long successRows;
    private long failedRows;
    private String errorFilePath;
    private String startedAt;
    private String finishedAt;

    /** 使用持久化数据还原导入任务。 */
    public ImportJob(
            String id,
            String projectId,
            String creatorId,
            String originalFileName,
            String storagePath,
            ImportJobStatus status,
            long totalRows,
            long successRows,
            long failedRows,
            String errorFilePath,
            String createdAt,
            String startedAt,
            String finishedAt
    ) {
        this.id = TextValues.requireNonBlank(id, "导入任务编号");
        this.projectId = TextValues.requireNonBlank(
                projectId,
                "项目编号"
        );
        this.creatorId = TextValues.requireNonBlank(
                creatorId,
                "创建者编号"
        );
        this.originalFileName = requireLength(
                originalFileName,
                "CSV 原始文件名",
                MAX_ORIGINAL_FILE_NAME_LENGTH
        );
        this.storagePath = requireLength(
                storagePath,
                "CSV 存储路径",
                MAX_STORAGE_PATH_LENGTH
        );
        this.status = Objects.requireNonNull(
                status,
                "导入任务状态不能为 null"
        );
        requireCounts(totalRows, successRows, failedRows);
        if (status == ImportJobStatus.COMPLETED
                && successRows + failedRows != totalRows) {
            throw new IllegalArgumentException(
                    "已完成导入任务的成功数与失败数之和必须等于总数"
            );
        }
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failedRows = failedRows;
        this.errorFilePath = requireOptionalLength(
                errorFilePath,
                "错误报告路径",
                MAX_STORAGE_PATH_LENGTH
        );
        this.createdAt = UtcTimeText.requireValid(
                createdAt,
                "导入任务创建时间"
        );
        this.startedAt = requireOptionalTimeAtOrAfter(
                startedAt,
                this.createdAt,
                "导入任务开始时间",
                "创建时间"
        );
        String finishedLowerBound = this.startedAt == null
                ? this.createdAt
                : this.startedAt;
        this.finishedAt = requireOptionalTimeAtOrAfter(
                finishedAt,
                finishedLowerBound,
                "导入任务结束时间",
                this.startedAt == null ? "创建时间" : "开始时间"
        );
        requireStateShape();
    }

    /** 创建统计为零的待执行导入任务。 */
    public static ImportJob create(
            String id,
            String projectId,
            String creatorId,
            String originalFileName,
            String storagePath,
            String now
    ) {
        return new ImportJob(
                id,
                projectId,
                creatorId,
                originalFileName,
                storagePath,
                ImportJobStatus.PENDING,
                0,
                0,
                0,
                null,
                now,
                null,
                null
        );
    }

    /** 原子抢占成功后把待执行任务推进为运行中。 */
    public void markRunning(String now) {
        if (status != ImportJobStatus.PENDING) {
            throw new IllegalStateException("只有待执行导入任务可以开始");
        }
        startedAt = UtcTimeText.requireAtOrAfter(
                now,
                createdAt,
                "导入任务开始时间",
                "创建时间"
        );
        status = ImportJobStatus.RUNNING;
    }

    /** 将运行中的任务标记为完成并保存最终统计。 */
    public void markCompleted(
            long totalRows,
            long successRows,
            long failedRows,
            String errorFilePath,
            String now
    ) {
        finish(
                ImportJobStatus.COMPLETED,
                totalRows,
                successRows,
                failedRows,
                errorFilePath,
                now
        );
    }

    /** 将运行中的任务标记为失败并保存已产生的统计。 */
    public void markFailed(
            long totalRows,
            long successRows,
            long failedRows,
            String errorFilePath,
            String now
    ) {
        finish(
                ImportJobStatus.FAILED,
                totalRows,
                successRows,
                failedRows,
                errorFilePath,
                now
        );
    }

    private void finish(
            ImportJobStatus targetStatus,
            long totalRows,
            long successRows,
            long failedRows,
            String errorFilePath,
            String now
    ) {
        if (status != ImportJobStatus.RUNNING) {
            throw new IllegalStateException("只有运行中的导入任务可以结束");
        }
        requireCounts(totalRows, successRows, failedRows);
        if (targetStatus == ImportJobStatus.COMPLETED
                && successRows + failedRows != totalRows) {
            throw new IllegalArgumentException(
                    "已完成导入任务的成功数与失败数之和必须等于总数"
            );
        }
        String validErrorFilePath = requireOptionalLength(
                errorFilePath,
                "错误报告路径",
                MAX_STORAGE_PATH_LENGTH
        );
        String validFinishedAt = UtcTimeText.requireAtOrAfter(
                now,
                startedAt,
                "导入任务结束时间",
                "开始时间"
        );
        this.status = targetStatus;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.failedRows = failedRows;
        this.errorFilePath = validErrorFilePath;
        this.finishedAt = validFinishedAt;
    }

    private void requireStateShape() {
        switch (status) {
            case PENDING -> {
                if (startedAt != null || finishedAt != null
                        || totalRows != 0 || successRows != 0
                        || failedRows != 0 || errorFilePath != null) {
                    throw new IllegalArgumentException(
                            "待执行导入任务不能包含执行结果"
                    );
                }
            }
            case RUNNING -> {
                if (startedAt == null || finishedAt != null) {
                    throw new IllegalArgumentException(
                            "运行中导入任务必须有开始时间且不能有结束时间"
                    );
                }
            }
            case COMPLETED, FAILED -> {
                if (startedAt == null || finishedAt == null) {
                    throw new IllegalArgumentException(
                            "已结束导入任务必须包含开始和结束时间"
                    );
                }
            }
        }
    }

    private static void requireCounts(
            long totalRows,
            long successRows,
            long failedRows
    ) {
        if (totalRows < 0 || successRows < 0 || failedRows < 0) {
            throw new IllegalArgumentException("导入行数统计不能为负数");
        }
        if (successRows > totalRows || failedRows > totalRows
                || successRows + failedRows > totalRows) {
            throw new IllegalArgumentException("导入行数统计彼此不一致");
        }
    }

    private static String requireLength(
            String value,
            String fieldName,
            int maxLength
    ) {
        String validValue = TextValues.requireNonBlank(value, fieldName);
        if (validValue.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过 " + maxLength + " 个字符"
            );
        }
        return validValue;
    }

    private static String requireOptionalLength(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }
        return requireLength(value, fieldName, maxLength);
    }

    private static String requireOptionalTimeAtOrAfter(
            String value,
            String lowerBound,
            String fieldName,
            String lowerBoundFieldName
    ) {
        return value == null ? null : UtcTimeText.requireAtOrAfter(
                value,
                lowerBound,
                fieldName,
                lowerBoundFieldName
        );
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public long getSuccessRows() {
        return successRows;
    }

    public long getFailedRows() {
        return failedRows;
    }

    public String getErrorFilePath() {
        return errorFilePath;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }
}
