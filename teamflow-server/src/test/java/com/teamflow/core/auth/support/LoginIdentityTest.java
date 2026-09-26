package com.teamflow.core.auth.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 登录标识规范化测试。 */
class LoginIdentityTest {

    @Test
    void shouldNormalizeUsernameAndEmailOnce() {
        assertEquals("zhou_01", LoginIdentity.normalize(" Zhou_01 "));
        assertEquals(
                "zhou@example.com",
                LoginIdentity.normalize(" ZHOU@EXAMPLE.COM ")
        );
    }

    @Test
    void shouldRejectNullIdentity() {
        assertThrows(
                NullPointerException.class,
                () -> LoginIdentity.normalize(null)
        );
    }
}
