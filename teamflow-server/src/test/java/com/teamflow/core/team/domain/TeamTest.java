package com.teamflow.core.team.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 团队实体、时间文本和乐观锁状态测试。 */
class TeamTest {

    private static final String CREATED_AT = "2026-09-12T01:02:03.456Z";
    private static final String CHANGED_AT = "2026-09-12T02:03:04.567Z";

    @Test
    void shouldCreateNormalizedTeamWithStringIdsAndInitialVersion() {
        Team team = Team.create(
                "tm001",
                " 研发团队 ",
                " 负责 TeamFlow 研发 ",
                "u001",
                CREATED_AT
        );

        assertAll(
                () -> assertEquals("tm001", team.getId()),
                () -> assertEquals("研发团队", team.getName()),
                () -> assertEquals("负责 TeamFlow 研发", team.getDescription()),
                () -> assertEquals("u001", team.getOwnerId()),
                () -> assertEquals(0, team.getVersion()),
                () -> assertEquals(CREATED_AT, team.getCreatedAt()),
                () -> assertEquals(CREATED_AT, team.getUpdatedAt())
        );
    }

    @Test
    void shouldNormalizeBlankDescriptionToNull() {
        Team team = Team.create(
                "tm001",
                "研发团队",
                "   ",
                "u001",
                CREATED_AT
        );

        assertNull(team.getDescription());
    }

    @Test
    void shouldUpdateDetailsVersionAndUtcTimeTogether() {
        Team team = newTeam();

        boolean changed = team.updateDetails(
                " 产品团队 ",
                " 新描述 ",
                CHANGED_AT
        );

        assertAll(
                () -> assertTrue(changed),
                () -> assertEquals("产品团队", team.getName()),
                () -> assertEquals("新描述", team.getDescription()),
                () -> assertEquals(1, team.getVersion()),
                () -> assertEquals(CHANGED_AT, team.getUpdatedAt())
        );
    }

    @Test
    void shouldTreatNormalizedSameDetailsAsNoOp() {
        Team team = newTeam();

        boolean changed = team.updateDetails(
                " 研发团队 ",
                " 初始描述 ",
                CHANGED_AT
        );

        assertAll(
                () -> assertFalse(changed),
                () -> assertEquals(0, team.getVersion()),
                () -> assertEquals(CREATED_AT, team.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectInvalidPersistentState() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredTeam(-1, CREATED_AT, CREATED_AT)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredTeam(
                                0,
                                "2026-09-12T01:02:03Z",
                                CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredTeam(
                                0,
                                CREATED_AT,
                                "2026-09-12T01:02:03.455Z"
                        )
                )
        );
    }

    @Test
    void shouldRejectEarlierUpdateWithoutChangingState() {
        Team team = newTeam();

        assertThrows(
                IllegalArgumentException.class,
                () -> team.updateDetails(
                        "产品团队",
                        null,
                        "2026-09-12T01:02:03.455Z"
                )
        );

        assertAll(
                () -> assertEquals("研发团队", team.getName()),
                () -> assertEquals("初始描述", team.getDescription()),
                () -> assertEquals(0, team.getVersion()),
                () -> assertEquals(CREATED_AT, team.getUpdatedAt())
        );
    }

    @Test
    void shouldKeepStateWhenVersionOverflows() {
        Team team = restoredTeam(
                Integer.MAX_VALUE,
                CREATED_AT,
                CREATED_AT
        );

        assertThrows(
                IllegalStateException.class,
                () -> team.updateDetails(
                        "产品团队",
                        null,
                        CHANGED_AT
                )
        );

        assertAll(
                () -> assertEquals("研发团队", team.getName()),
                () -> assertEquals("初始描述", team.getDescription()),
                () -> assertEquals(Integer.MAX_VALUE, team.getVersion()),
                () -> assertEquals(CREATED_AT, team.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectMissingOrBlankRequiredIdentity() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Team.create(
                                null, "研发团队", null, "u001", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Team.create(
                                "tm001", " ", null, "u001", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Team.create(
                                "tm001", "研发团队", null, " ", CREATED_AT
                        )
                )
        );
    }

    private Team newTeam() {
        return Team.create(
                "tm001",
                "研发团队",
                "初始描述",
                "u001",
                CREATED_AT
        );
    }

    private Team restoredTeam(
            int version,
            String createdAt,
            String updatedAt
    ) {
        return new Team(
                "tm001",
                "研发团队",
                "初始描述",
                "u001",
                version,
                createdAt,
                updatedAt
        );
    }
}
