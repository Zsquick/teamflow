package com.teamflow.security;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.auth.error.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 认证异常业务翻译测试。 */
class AuthenticationFailureTranslatorTest {

    private static final String INTERNAL_MESSAGE =
            "SENSITIVE_AUTHENTICATION_DETAIL";

    private final AuthenticationFailureTranslator translator =
            new AuthenticationFailureTranslator();

    @ParameterizedTest
    @MethodSource("expectedFailures")
    void shouldTranslateExpectedFailureToStableErrorCode(
            AuthenticationException source,
            AuthErrorCode expectedErrorCode
    ) {
        BusinessException translated = translator.translate(source);

        assertEquals(expectedErrorCode, translated.getErrorCode());
        assertEquals(expectedErrorCode.message(), translated.getMessage());
        assertFalse(translated.getMessage().contains(INTERNAL_MESSAGE));
    }

    @Test
    void shouldPropagateUnexpectedAuthenticationFailure() {
        AuthenticationServiceException source =
                new AuthenticationServiceException(INTERNAL_MESSAGE);

        AuthenticationServiceException thrown = assertThrows(
                AuthenticationServiceException.class,
                () -> translator.translate(source)
        );

        assertSame(source, thrown);
    }

    @Test
    void shouldRejectMissingAuthenticationException() {
        assertThrows(
                NullPointerException.class,
                () -> translator.translate(null)
        );
    }

    private static Stream<Arguments> expectedFailures() {
        return Stream.of(
                Arguments.of(
                        new BadCredentialsException(INTERNAL_MESSAGE),
                        AuthErrorCode.INVALID_CREDENTIALS
                ),
                Arguments.of(
                        new UsernameNotFoundException(INTERNAL_MESSAGE),
                        AuthErrorCode.INVALID_CREDENTIALS
                ),
                Arguments.of(
                        new DisabledException(INTERNAL_MESSAGE),
                        AuthErrorCode.ACCOUNT_DISABLED
                ),
                Arguments.of(
                        new LockedException(INTERNAL_MESSAGE),
                        AuthErrorCode.ACCOUNT_LOCKED
                )
        );
    }
}
