package com.teamflow.core.audit.service;

import com.teamflow.core.audit.domain.OperationLog;

/**
 * 操作审计日志业务契约。
 */
public interface OperationLogService {

    /**
     * 保存一条操作审计记录。
     *
     * @param operationLog 操作日志
     */
    void record(OperationLog operationLog);
}

