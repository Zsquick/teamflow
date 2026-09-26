package com.teamflow.core.user.support;

import com.teamflow.common.validation.TextValues;

/** 将用户名和邮箱转换为可用于身份确认、但不暴露完整账号的文本。 */
public final class UserIdentityMasker {

    private UserIdentityMasker() {
    }

    public static String maskUsername(String username) {
        String value = TextValues.requireNonBlank(username, "用户名");
        if (value.length() == 1) {
            return "*";
        }
        if (value.length() == 2) {
            return value.charAt(0) + "*";
        }
        return value.charAt(0) + "*".repeat(value.length() - 2)
                + value.charAt(value.length() - 1);
    }

    public static String maskEmail(String email) {
        String value = TextValues.requireNonBlank(email, "邮箱");
        int separator = value.indexOf('@');
        if (separator <= 0 || separator == value.length() - 1) {
            return maskUsername(value);
        }
        return maskUsername(value.substring(0, separator))
                + value.substring(separator);
    }
}
