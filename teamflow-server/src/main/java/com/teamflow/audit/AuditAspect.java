package com.teamflow.audit;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.audit.domain.OperationLog;
import com.teamflow.core.audit.service.OperationLogService;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 在带有 {@link Audited} 的请求方法外记录安全、可检索的操作摘要。
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(
            AuditAspect.class
    );
    private static final Pattern RESOURCE_ID_EXPRESSION = Pattern.compile(
            "^#[A-Za-z_][A-Za-z0-9_]*"
                    + "(?:\\.[A-Za-z_][A-Za-z0-9_]*)*$"
    );

    private final OperationLogService operationLogService;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;
    private final ExpressionParser expressionParser =
            new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache =
            new ConcurrentHashMap<>();

    public AuditAspect(
            OperationLogService operationLogService,
            ReadableIdGenerator idGenerator,
            Clock clock
    ) {
        this.operationLogService = Objects.requireNonNull(
                operationLogService,
                "操作日志服务不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "审计时钟不能为 null");
    }

    /**
     * 原样保留业务返回或异常，并在业务结束后尽力追加审计记录。
     */
    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Objects.requireNonNull(joinPoint, "连接点不能为 null");
        Objects.requireNonNull(audited, "审计注解不能为 null");

        long startedAtNanos = System.nanoTime();
        Throwable businessFailure = null;
        boolean success = false;
        try {
            Object result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable throwable) {
            businessFailure = throwable;
            throw throwable;
        } finally {
            recordSafely(
                    joinPoint,
                    audited,
                    success,
                    businessFailure,
                    elapsedMillis(startedAtNanos)
            );
        }
    }

    private void recordSafely(
            ProceedingJoinPoint joinPoint,
            Audited audited,
            boolean success,
            Throwable businessFailure,
            long durationMs
    ) {
        try {
            HttpServletRequest request = currentRequest();
            OperationLog operationLog = new OperationLog(
                    idGenerator.nextId(ResourceType.OPERATION_LOG),
                    currentUserId(),
                    audited.action(),
                    audited.resourceType(),
                    resolveResourceId(joinPoint, audited.resourceId()),
                    safeDetail(joinPoint, businessFailure),
                    success,
                    durationMs,
                    traceId(request),
                    ipAddress(request),
                    UtcTimeText.now(clock)
            );
            operationLogService.record(operationLog);
        } catch (RuntimeException auditFailure) {
            log.warn(
                    "操作审计降级 action={} resourceType={} success={} "
                            + "auditFailureType={}",
                    audited.action(),
                    audited.resourceType(),
                    success,
                    auditFailure.getClass().getName()
            );
        }
    }

    private String resolveResourceId(
            ProceedingJoinPoint joinPoint,
            String source
    ) {
        if (source == null || source.isBlank()) {
            return null;
        }
        if (!RESOURCE_ID_EXPRESSION.matcher(source).matches()) {
            throw new IllegalArgumentException(
                    "资源编号表达式只允许 #参数名.只读属性链"
            );
        }

        Method method = ((MethodSignature) joinPoint.getSignature())
                .getMethod();
        String[] parameterNames = parameterNameDiscoverer
                .getParameterNames(method);
        if (parameterNames == null) {
            throw new IllegalStateException("无法读取审计方法参数名");
        }

        Object[] arguments = joinPoint.getArgs();
        if (parameterNames.length != arguments.length) {
            throw new IllegalStateException("审计方法参数元数据不一致");
        }

        SimpleEvaluationContext context = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .build();
        for (int index = 0; index < parameterNames.length; index++) {
            context.setVariable(parameterNames[index], arguments[index]);
        }

        Expression expression = expressionCache.computeIfAbsent(
                source,
                expressionParser::parseExpression
        );
        Object value = expression.getValue(context);
        if (value == null) {
            return null;
        }
        if (!(value instanceof CharSequence resourceId)) {
            throw new IllegalArgumentException(
                    "资源编号表达式结果必须是字符串"
            );
        }
        return resourceId.toString();
    }

    private static String safeDetail(
            ProceedingJoinPoint joinPoint,
            Throwable businessFailure
    ) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String handler = signature.getDeclaringType().getSimpleName()
                + "#"
                + signature.getName();
        if (businessFailure == null) {
            return handler;
        }
        if (businessFailure instanceof BusinessException exception) {
            return handler
                    + ";errorCode="
                    + exception.getErrorCode().code();
        }
        return handler
                + ";errorType="
                + businessFailure.getClass().getSimpleName();
    }

    private static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder
                .getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private static String traceId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(
                RequestTraceFilter.TRACE_ID_ATTRIBUTE
        );
        if (value instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        return null;
    }

    private static String ipAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank()
                ? null
                : remoteAddress;
    }

    private static long elapsedMillis(long startedAtNanos) {
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        return Math.max(
                0L,
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
        );
    }
}
