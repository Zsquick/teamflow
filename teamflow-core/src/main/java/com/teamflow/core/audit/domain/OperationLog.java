package com.teamflow.core.audit.domain;

import com.teamflow.common.validation.NumberValues;
import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;

/**
 * 用户业务操作审计记录，只保存可检索的安全摘要而不保存请求正文。
 */
public class OperationLog {

    private static final int MAX_ID_LENGTH = 32;
    private static final int MAX_ACTION_LENGTH = 64;
    private static final int MAX_RESOURCE_TYPE_LENGTH = 64;
    private static final int MAX_DETAIL_LENGTH = 500;
    private static final int MAX_TRACE_ID_LENGTH = 64;
    private static final int MAX_IP_ADDRESS_LENGTH = 45;

    private final String id;
    private final String userId;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final String detail;
    private final boolean success;
    private final long durationMs;
    private final String traceId;
    private final String ipAddress;
    private final String createdAt;

    /** 使用持久化字段创建一条不可变审计记录。 */
    public OperationLog(
            String id,
            String userId,
            String action,
            String resourceType,
            String resourceId,
            String detail,
            boolean success,
            long durationMs,
            String traceId,
            String ipAddress,
            String createdAt
    ) {
        this.id = requireSized(id, "操作日志编号", MAX_ID_LENGTH);
        this.userId = requireOptionalSized(
                userId,
                "操作用户编号",
                MAX_ID_LENGTH
        );
        this.action = requireSized(
                action,
                "审计动作",
                MAX_ACTION_LENGTH
        );
        this.resourceType = requireSized(
                resourceType,
                "资源类型",
                MAX_RESOURCE_TYPE_LENGTH
        );
        this.resourceId = requireOptionalSized(
                resourceId,
                "资源编号",
                MAX_ID_LENGTH
        );
        this.detail = requireOptionalSized(
                detail,
                "审计详情",
                MAX_DETAIL_LENGTH
        );
        this.success = success;
        this.durationMs = NumberValues.requireNonNegative(
                durationMs,
                "操作耗时"
        );
        this.traceId = requireOptionalSized(
                traceId,
                "调用链编号",
                MAX_TRACE_ID_LENGTH
        );
        this.ipAddress = requireOptionalSized(
                ipAddress,
                "IP 地址",
                MAX_IP_ADDRESS_LENGTH
        );
        this.createdAt = UtcTimeText.requireValid(
                createdAt,
                "操作日志创建时间"
        );
    }

    private static String requireSized(
            String value,
            String fieldName,
            int maxLength
    ) {
        String valid = TextValues.requireNonBlank(value, fieldName);
        if (valid.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过 " + maxLength + " 个字符"
            );
        }
        return valid;
    }

    private static String requireOptionalSized(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }
        return requireSized(value, fieldName, maxLength);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
