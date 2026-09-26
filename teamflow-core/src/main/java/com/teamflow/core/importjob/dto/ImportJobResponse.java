package com.teamflow.core.importjob.dto;

import com.teamflow.core.importjob.domain.ImportJobStatus;
/**
 * CSV 导入任务响应。
 *
 * @param id 导入任务标识
 * @param projectId 项目标识
 * @param status 状态
 * @param totalRows 总行数
 * @param successRows 成功行数
 * @param failedRows 失败行数
 * @param createdAt 创建时间
 * @param finishedAt 完成时间
 */
public record ImportJobResponse(
        String id,
        String projectId,
        ImportJobStatus status,
        long totalRows,
        long successRows,
        long failedRows,
        String createdAt,
        String finishedAt
) {
    /** 从持久化实体创建不包含服务器路径的公开响应。 */
    public static ImportJobResponse from(
            com.teamflow.core.importjob.domain.ImportJob importJob
    ) {
        java.util.Objects.requireNonNull(
                importJob,
                "导入任务不能为 null"
        );
        return new ImportJobResponse(
                importJob.getId(),
                importJob.getProjectId(),
                importJob.getStatus(),
                importJob.getTotalRows(),
                importJob.getSuccessRows(),
                importJob.getFailedRows(),
                importJob.getCreatedAt(),
                importJob.getFinishedAt()
        );
    }
}
