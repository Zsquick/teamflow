package com.teamflow.core.file.dto;

import com.teamflow.core.file.domain.Attachment;

import java.util.Objects;

/**
 * 附件元数据响应。
 *
 * @param id 附件标识
 * @param taskId 任务标识
 * @param uploaderId 上传者标识
 * @param originalName 原始文件名
 * @param contentType 媒体类型
 * @param size 文件字节数
 * @param sha256 文件摘要
 * @param createdAt 上传时间
 */
public record AttachmentResponse(
        String id,
        String taskId,
        String uploaderId,
        String originalName,
        String contentType,
        long size,
        String sha256,
        String createdAt
) {

    /** 将附件领域对象转换为不含内部存储路径的接口响应。 */
    public static AttachmentResponse from(Attachment attachment) {
        Objects.requireNonNull(attachment, "附件不能为 null");
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTaskId(),
                attachment.getUploaderId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSize(),
                attachment.getSha256(),
                attachment.getCreatedAt()
        );
    }
}
