package com.teamflow.core.comment.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 评论业务错误码协议测试。 */
class CommentErrorCodeTest {

    @Test
    void shouldExposeStableMappings() {
        assertMapping(
                CommentErrorCode.COMMENT_NOT_FOUND,
                "COMMENT_0001",
                "评论不存在或无权访问",
                404
        );
        assertMapping(
                CommentErrorCode.COMMENT_DELETE_FORBIDDEN,
                "COMMENT_0002",
                "只能删除自己发表的评论",
                403
        );
    }

    @Test
    void shouldKeepEveryCommentCodeUniqueAndNamespaced() {
        Set<String> codes = Arrays.stream(CommentErrorCode.values())
                .map(CommentErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(CommentErrorCode.values().length, codes.size());
        assertTrue(codes.stream().allMatch(code ->
                code.startsWith("COMMENT_")
        ));
    }

    private static void assertMapping(
            CommentErrorCode errorCode,
            String code,
            String message,
            int httpStatus
    ) {
        assertEquals(code, errorCode.code());
        assertEquals(message, errorCode.message());
        assertEquals(httpStatus, errorCode.httpStatus());
    }
}
