package com.teamflow.core.comment.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 评论领域对象的规范化与持久化不变量测试。 */
class TaskCommentTest {

    private static final String CREATED_AT =
            "2026-09-18T01:02:03.456Z";

    @Test
    void shouldCreateNormalizedImmutableComment() {
        TaskComment comment = TaskComment.create(
                "c001",
                "t001",
                "u001",
                "  协作信息\n  ",
                CREATED_AT
        );

        assertAll(
                () -> assertEquals("c001", comment.getId()),
                () -> assertEquals("t001", comment.getTaskId()),
                () -> assertEquals("u001", comment.getAuthorId()),
                () -> assertEquals("协作信息", comment.getContent()),
                () -> assertEquals(CREATED_AT, comment.getCreatedAt())
        );
    }

    @Test
    void shouldAcceptContentAtDatabaseLengthBoundary() {
        TaskComment comment = TaskComment.create(
                "c001",
                "t001",
                "u001",
                "评".repeat(2000),
                CREATED_AT
        );

        assertEquals(2000, comment.getContent().length());
    }

    @Test
    void shouldRejectBlankOrOversizedContent() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TaskComment.create(
                                "c001", "t001", "u001", "  ",
                                CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TaskComment.create(
                                "c001", "t001", "u001",
                                "评".repeat(2001), CREATED_AT
                        )
                )
        );
    }

    @Test
    void shouldRejectInvalidPersistentValues() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new TaskComment(
                                null, "t001", "u001", "评论", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TaskComment(
                                "c001", " ", "u001", "评论", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TaskComment(
                                "c001", "t001", " ", "评论", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TaskComment(
                                "c001", "t001", "u001", "评论",
                                "2026-09-18T01:02:03Z"
                        )
                )
        );
    }
}
