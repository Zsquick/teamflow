package com.teamflow.core.audit.service;

import com.teamflow.core.audit.domain.OperationLog;
import com.teamflow.core.audit.mapper.OperationLogMapper;
import com.teamflow.core.audit.service.impl.OperationLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 操作日志独立事务和单行追加测试。 */
@ExtendWith(MockitoExtension.class)
class OperationLogServiceImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    private OperationLogServiceImpl operationLogService;

    @BeforeEach
    void setUp() {
        operationLogService = new OperationLogServiceImpl(operationLogMapper);
    }

    @Test
    void shouldRequireMapperAndOperationLog() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new OperationLogServiceImpl(null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> operationLogService.record(null)
                )
        );
    }

    @Test
    void shouldAppendExactlyOneOperationLog() {
        OperationLog operationLog = operationLog();
        when(operationLogMapper.insert(operationLog)).thenReturn(1);

        operationLogService.record(operationLog);

        verify(operationLogMapper).insert(operationLog);
    }

    @Test
    void shouldRejectUnexpectedInsertCounts() {
        OperationLog operationLog = operationLog();
        when(operationLogMapper.insert(operationLog)).thenReturn(0, 2);

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> operationLogService.record(operationLog)
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> operationLogService.record(operationLog)
                )
        );
    }

    @Test
    void shouldAlwaysUseIndependentTransaction() throws Exception {
        Method method = OperationLogServiceImpl.class.getMethod(
                "record",
                OperationLog.class
        );
        Transactional transactional = method.getAnnotation(
                Transactional.class
        );

        assertAll(
                () -> assertNotNull(transactional),
                () -> assertEquals(
                        Propagation.REQUIRES_NEW,
                        transactional.propagation()
                )
        );
    }

    private static OperationLog operationLog() {
        return new OperationLog(
                "op001",
                "u001",
                "TASK_UPDATE",
                "TASK",
                "t001",
                "TaskController#update",
                true,
                3L,
                "trace",
                "127.0.0.1",
                "2026-09-18T01:02:03.456Z"
        );
    }
}
