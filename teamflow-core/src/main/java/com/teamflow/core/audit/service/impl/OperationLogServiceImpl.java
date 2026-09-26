package com.teamflow.core.audit.service.impl;

import com.teamflow.core.audit.domain.OperationLog;
import com.teamflow.core.audit.mapper.OperationLogMapper;
import com.teamflow.core.audit.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 在独立事务中追加操作日志的默认实现。
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {
    private final OperationLogMapper operationLogMapper;

    /**
     * 创建操作日志业务实现。
     *
     * @param operationLogMapper 操作日志数据访问接口
     */
    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = Objects.requireNonNull(
                operationLogMapper,
                "操作日志 Mapper 不能为 null"
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(OperationLog operationLog) {
        Objects.requireNonNull(operationLog, "操作日志不能为 null");
        int affectedRows = operationLogMapper.insert(operationLog);
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增操作日志时数据库影响行数必须为 1"
            );
        }
    }
}
