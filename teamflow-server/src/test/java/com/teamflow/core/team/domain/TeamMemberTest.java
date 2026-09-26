package com.teamflow.core.team.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 团队成员关系持久化状态测试。 */
class TeamMemberTest {

    private static final String JOINED_AT = "2026-09-12T01:02:03.456Z";

    @Test
    void shouldCreateStringIdOwnerMembershipWithUtcTime() {
        TeamMember member = TeamMember.create(
                "mb001",
                "tm001",
                "u001",
                TeamRole.OWNER,
                JOINED_AT
        );

        assertAll(
                () -> assertEquals("mb001", member.getId()),
                () -> assertEquals("tm001", member.getTeamId()),
                () -> assertEquals("u001", member.getUserId()),
                () -> assertEquals(TeamRole.OWNER, member.getRole()),
                () -> assertEquals(JOINED_AT, member.getJoinedAt())
        );
    }

    @Test
    void shouldRejectMissingOrBlankIdentityAndRole() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> TeamMember.create(
                                null, "tm001", "u001", TeamRole.MEMBER, JOINED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TeamMember.create(
                                "mb001", " ", "u001", TeamRole.MEMBER, JOINED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TeamMember.create(
                                "mb001", "tm001", " ", TeamRole.MEMBER, JOINED_AT
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> TeamMember.create(
                                "mb001", "tm001", "u001", null, JOINED_AT
                        )
                )
        );
    }

    @Test
    void shouldRejectNonCanonicalUtcTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TeamMember.create(
                        "mb001",
                        "tm001",
                        "u001",
                        TeamRole.MEMBER,
                        "2026-09-12T01:02:03Z"
                )
        );
    }
}
