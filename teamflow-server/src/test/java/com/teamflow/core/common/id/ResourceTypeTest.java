package com.teamflow.core.common.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 资源类型与编号格式测试。 */
class ResourceTypeTest {

    @Test
    void shouldExposeStableSequenceNameAndPrefix() {
        assertEquals("USER", ResourceType.USER.sequenceName());
        assertEquals("u", ResourceType.USER.prefix());
        assertEquals("TEAM", ResourceType.TEAM.sequenceName());
        assertEquals("tm", ResourceType.TEAM.prefix());
        assertEquals("TEAM_MEMBER", ResourceType.TEAM_MEMBER.sequenceName());
        assertEquals("mb", ResourceType.TEAM_MEMBER.prefix());
    }

    @Test
    void shouldFormatSequenceWithAtLeastThreeDigits() {
        assertEquals("u001", ResourceType.USER.format(1));
        assertEquals("tm001", ResourceType.TEAM.format(1));
        assertEquals("mb001", ResourceType.TEAM_MEMBER.format(1));
        assertEquals("t012", ResourceType.TASK.format(12));
        assertEquals("p1234", ResourceType.PROJECT.format(1234));
    }

    @Test
    void shouldRejectNonPositiveSequenceValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ResourceType.USER.format(0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ResourceType.USER.format(-1)
        );
    }
}
