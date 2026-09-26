package com.teamflow.core.auth.support;

import java.util.Locale;
import java.util.Objects;

/**
 * 统一规范化用于注册和登录的用户名或邮箱。
 */
public final class LoginIdentity {

    private LoginIdentity() {
    }

    /**
     * 去除首尾空白，并使用与运行环境无关的小写规则转换登录标识。
     *
     * @param value 用户名或邮箱
     * @return 规范化后的登录标识
     */
    public static String normalize(String value) {
        return Objects.requireNonNull(value, "登录标识不能为 null")
                .strip()
                .toLowerCase(Locale.ROOT);
    }
}
