package com.teamflow.core.dashboard.error;

import com.teamflow.common.error.ErrorCode;

/** 项目仪表盘并行聚合使用的稳定业务错误码。 */
public enum DashboardErrorCode implements ErrorCode {
    STATISTICS_TIMEOUT(
            "DASHBOARD_0001",
            "仪表盘统计查询超时，请稍后重试",
            504
    ),
    EXECUTOR_BUSY(
            "DASHBOARD_0002",
            "仪表盘请求过多，请稍后重试",
            503
    ),
    STATISTICS_UNAVAILABLE(
            "DASHBOARD_0003",
            "仪表盘统计暂时不可用，请稍后重试",
            503
    );

    private final String code;
    private final String message;
    private final int httpStatus;

    DashboardErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
