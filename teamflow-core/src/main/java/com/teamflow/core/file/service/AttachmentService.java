package com.teamflow.core.file.service;

import com.teamflow.core.file.dto.AttachmentResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 任务附件业务契约。
 */
public interface AttachmentService {

    /**
     * 保存任务附件内容和元数据。
     *
     * @param currentUserId 当前用户标识
     * @param taskId 任务标识
     * @param originalName 原始文件名
     * @param contentType 媒体类型
     * @param size 文件字节数
     * @param inputStream 文件输入流
     * @return 附件元数据
     * @throws IOException 文件读写失败
     */
    AttachmentResponse upload(
            String currentUserId,
            String taskId,
            String originalName,
            String contentType,
            long size,
            InputStream inputStream
    ) throws IOException;

    /**
     * 查询任务附件。
     *
     * @param currentUserId 当前用户标识
     * @param taskId 任务标识
     * @return 附件列表
     */
    List<AttachmentResponse> listByTask(
            String currentUserId,
            String taskId
    );

    /**
     * 打开有权访问的附件。
     *
     * @param currentUserId 当前用户标识
     * @param attachmentId 附件标识
     * @return 下载信息
     */
    AttachmentDownload download(
            String currentUserId,
            String attachmentId
    );

    /**
     * 删除附件内容和元数据。
     *
     * @param currentUserId 当前用户标识
     * @param attachmentId 附件标识
     */
    void delete(String currentUserId, String attachmentId);
}
