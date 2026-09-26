package com.teamflow.common.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 创建资源后返回的统一标识结构。
 *
 * @param id 新资源标识
 */
public record IdResponse(String id) {

    private static final Pattern ID_PATTERN =
            Pattern.compile("[a-z]{1,8}(?!0+$)[0-9]{3,}");

    /**
     * 校验创建成功后返回的资源标识。
     */
    public IdResponse {
        Objects.requireNonNull(id, "资源 ID 不能为 null");

        if (id.length() > 32 || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "资源 ID 必须由 1 到 8 个小写字母前缀和至少 3 位数字组成"
            );
        }
    }
}
