package com.teamflow.ratelimit;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;

/** 携带客户端重试等待秒数的 HTTP 限流异常。 */
public final class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(CommonErrorCode.TOO_MANY_REQUESTS);
        if (retryAfterSeconds < 1L) {
            throw new IllegalArgumentException("重试等待秒数必须大于 0");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
