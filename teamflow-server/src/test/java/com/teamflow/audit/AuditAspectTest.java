package com.teamflow.audit;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;
import com.teamflow.core.audit.domain.OperationLog;
import com.teamflow.core.audit.service.OperationLogService;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.security.AuthenticatedUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 审计切面的成功、失败、降级与敏感信息隔离测试。 */
@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    private static final String NOW = "2026-09-18T01:02:03.456Z";
    private static final String TRACE_ID =
            "0123456789abcdef0123456789abcdef";
    private static final AuthenticatedUser USER = new AuthenticatedUser(
            "u001",
            "zhou",
            Set.of("ROLE_USER")
    );

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private ReadableIdGenerator idGenerator;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(
                operationLogService,
                idGenerator,
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        );
        lenient().when(idGenerator.nextId(ResourceType.OPERATION_LOG))
                .thenReturn("op001");
    }

    @AfterEach
    void clearThreadContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldRequireEveryDependency() {
        Clock clock = Clock.systemUTC();

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AuditAspect(null, idGenerator, clock)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AuditAspect(operationLogService, null, clock)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new AuditAspect(
                                operationLogService,
                                idGenerator,
                                null
                        )
                )
        );
    }

    @Test
    void shouldRecordAuthenticatedSuccessWithReadOnlyPropertyChain()
            throws Throwable {
        SensitiveRequest request = new SensitiveRequest(
                "never-log-password",
                "never-log-token",
                "never-log-comment-content",
                new ResourceReference("t001")
        );
        Audited audited = prepareJoinPoint(
                "create",
                new Class<?>[]{SensitiveRequest.class},
                new Object[]{request}
        );
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);
        bindAuthenticatedRequest();

        Object actualResult = auditAspect.around(joinPoint, audited);

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(
                OperationLog.class
        );
        verify(operationLogService).record(logCaptor.capture());
        OperationLog operationLog = logCaptor.getValue();
        assertAll(
                () -> assertSame(expectedResult, actualResult),
                () -> assertEquals("op001", operationLog.getId()),
                () -> assertEquals("u001", operationLog.getUserId()),
                () -> assertEquals("COMMENT_CREATE", operationLog.getAction()),
                () -> assertEquals("TASK", operationLog.getResourceType()),
                () -> assertEquals("t001", operationLog.getResourceId()),
                () -> assertEquals(
                        "AuditProbe#create",
                        operationLog.getDetail()
                ),
                () -> assertTrue(operationLog.isSuccess()),
                () -> assertTrue(operationLog.getDurationMs() >= 0L),
                () -> assertEquals(TRACE_ID, operationLog.getTraceId()),
                () -> assertEquals(
                        "203.0.113.8",
                        operationLog.getIpAddress()
                ),
                () -> assertEquals(NOW, operationLog.getCreatedAt()),
                () -> assertSensitiveValuesAbsent(operationLog, request)
        );
    }

    @Test
    void shouldRecordStableErrorCodeAndRethrowSameBusinessException()
            throws Throwable {
        Audited audited = prepareJoinPoint(
                "update",
                new Class<?>[]{String.class},
                new Object[]{"t001"}
        );
        BusinessException businessFailure = new BusinessException(
                CommonErrorCode.CONFLICT
        );
        when(joinPoint.proceed()).thenThrow(businessFailure);
        bindAuthenticatedRequest();

        Throwable actualFailure = assertThrows(
                Throwable.class,
                () -> auditAspect.around(joinPoint, audited)
        );

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(
                OperationLog.class
        );
        verify(operationLogService).record(logCaptor.capture());
        OperationLog operationLog = logCaptor.getValue();
        assertAll(
                () -> assertSame(businessFailure, actualFailure),
                () -> assertFalse(operationLog.isSuccess()),
                () -> assertEquals("t001", operationLog.getResourceId()),
                () -> assertEquals(
                        "AuditProbe#update;errorCode=COMMON_0006",
                        operationLog.getDetail()
                ),
                () -> assertFalse(
                        operationLog.getDetail().contains(
                                businessFailure.getMessage()
                        )
                )
        );
    }

    @Test
    void shouldNotTurnBusinessSuccessIntoFailureWhenAuditStorageFails()
            throws Throwable {
        Audited audited = prepareJoinPoint(
                "update",
                new Class<?>[]{String.class},
                new Object[]{"t001"}
        );
        Object expectedResult = new Object();
        when(joinPoint.proceed()).thenReturn(expectedResult);
        doThrow(new IllegalStateException("database-secret"))
                .when(operationLogService)
                .record(any(OperationLog.class));

        Object actualResult = auditAspect.around(joinPoint, audited);

        assertSame(expectedResult, actualResult);
        verify(operationLogService).record(any(OperationLog.class));
    }

    @Test
    void shouldPreserveOriginalFailureWhenAuditStorageAlsoFails()
            throws Throwable {
        Audited audited = prepareJoinPoint(
                "update",
                new Class<?>[]{String.class},
                new Object[]{"t001"}
        );
        IllegalArgumentException businessFailure =
                new IllegalArgumentException("business-secret");
        when(joinPoint.proceed()).thenThrow(businessFailure);
        doThrow(new IllegalStateException("audit-secret"))
                .when(operationLogService)
                .record(any(OperationLog.class));

        Throwable actualFailure = assertThrows(
                Throwable.class,
                () -> auditAspect.around(joinPoint, audited)
        );

        assertSame(businessFailure, actualFailure);
    }

    @Test
    void shouldAllowNonWebInvocationWithoutAuthenticationOrRequest()
            throws Throwable {
        Audited audited = prepareJoinPoint(
                "update",
                new Class<?>[]{String.class},
                new Object[]{"t001"}
        );
        when(joinPoint.proceed()).thenReturn("done");

        assertEquals("done", auditAspect.around(joinPoint, audited));

        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(
                OperationLog.class
        );
        verify(operationLogService).record(logCaptor.capture());
        assertAll(
                () -> assertNull(logCaptor.getValue().getUserId()),
                () -> assertNull(logCaptor.getValue().getTraceId()),
                () -> assertNull(logCaptor.getValue().getIpAddress())
        );
    }

    @Test
    void shouldRejectMethodCallsInResourceExpressionWithoutAffectingBusiness()
            throws Throwable {
        SensitiveRequest request = new SensitiveRequest(
                "password",
                "token",
                "content",
                new ResourceReference("t001")
        );
        Audited audited = AuditProbe.class.getDeclaredMethod(
                "invalidExpression",
                SensitiveRequest.class
        ).getAnnotation(Audited.class);
        when(joinPoint.proceed()).thenReturn("done");

        assertEquals("done", auditAspect.around(joinPoint, audited));

        verifyNoInteractions(operationLogService);
    }

    private Audited prepareJoinPoint(
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments
    ) throws NoSuchMethodException {
        Method method = AuditProbe.class.getDeclaredMethod(
                methodName,
                parameterTypes
        );
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(AuditProbe.class);
        when(signature.getName()).thenReturn(methodName);
        return method.getAnnotation(Audited.class);
    }

    private static void bindAuthenticatedRequest() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        USER,
                        null,
                        List.of()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.8");
        request.setAttribute(
                RequestTraceFilter.TRACE_ID_ATTRIBUTE,
                TRACE_ID
        );
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
    }

    private static void assertSensitiveValuesAbsent(
            OperationLog operationLog,
            SensitiveRequest request
    ) {
        List<String> persistedTexts = Stream.of(
                        operationLog.getId(),
                        operationLog.getUserId(),
                        operationLog.getAction(),
                        operationLog.getResourceType(),
                        operationLog.getResourceId(),
                        operationLog.getDetail(),
                        operationLog.getTraceId(),
                        operationLog.getIpAddress(),
                        operationLog.getCreatedAt()
                )
                .toList();
        for (String persistedText : persistedTexts) {
            assertFalse(persistedText.contains(request.password()));
            assertFalse(persistedText.contains(request.token()));
            assertFalse(persistedText.contains(request.content()));
        }
    }

    private record ResourceReference(String id) {
    }

    private record SensitiveRequest(
            String password,
            String token,
            String content,
            ResourceReference resource
    ) {
    }

    private static final class AuditProbe {

        @Audited(
                action = "COMMENT_CREATE",
                resourceType = "TASK",
                resourceId = "#request.resource.id"
        )
        private Object create(SensitiveRequest request) {
            return request;
        }

        @Audited(
                action = "TASK_UPDATE",
                resourceType = "TASK",
                resourceId = "#taskId"
        )
        private Object update(String taskId) {
            return taskId;
        }

        @Audited(
                action = "INVALID",
                resourceType = "TASK",
                resourceId = "#request.resource().id()"
        )
        private Object invalidExpression(SensitiveRequest request) {
            return request;
        }
    }
}
