package com.teamflow.core.importjob.service;

/**
 * 启动 Spring Batch 导入任务的端口。
 */
public interface ImportJobLauncher {

    /**
     * 异步启动指定导入任务。
     *
     * @param importJobId 导入任务标识
     */
    void launch(String importJobId);
}
