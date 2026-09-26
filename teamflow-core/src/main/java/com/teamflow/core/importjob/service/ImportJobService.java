package com.teamflow.core.importjob.service;

import com.teamflow.core.importjob.dto.ImportJobResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * CSV 导入任务业务契约。
 */
public interface ImportJobService {

    /**
     * 保存 CSV 并创建待执行的导入任务。
     *
     * @param currentUserId 当前用户标识
     * @param projectId 项目标识
     * @param originalName 原始文件名
     * @param contentType 上传方声明的媒体类型
     * @param size 文件大小
     * @param inputStream CSV 输入流
     * @return 导入任务
     * @throws IOException 文件保存失败
     */
    ImportJobResponse create(
            String currentUserId,
            String projectId,
            String originalName,
            String contentType,
            long size,
            InputStream inputStream
    ) throws IOException;

    /**
     * 查询导入任务状态。
     *
     * @param currentUserId 当前用户标识
     * @param importJobId 导入任务标识
     * @return 导入任务状态
     */
    ImportJobResponse get(String currentUserId, String importJobId);

    /**
     * 请求执行待处理的导入任务。
     *
     * @param currentUserId 当前用户标识
     * @param importJobId 导入任务标识
     */
    void start(String currentUserId, String importJobId);
}
