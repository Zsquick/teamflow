package com.teamflow.core.project.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 项目实体、项目键规范化、时间文本和乐观锁状态测试。 */
class ProjectTest {

    private static final String CREATED_AT = "2026-09-15T01:02:03.456Z";
    private static final String CHANGED_AT = "2026-09-15T02:03:04.567Z";

    @Test
    void shouldCreateNormalizedProjectWithReadableIdsAndInitialState() {
        Project project = Project.create(
                "p001",
                "tm001",
                " TeamFlow 服务端 ",
                " tf-api-2 ",
                " 核心接口项目 ",
                "u001",
                CREATED_AT
        );

        assertAll(
                () -> assertEquals("p001", project.getId()),
                () -> assertEquals("tm001", project.getTeamId()),
                () -> assertEquals("TeamFlow 服务端", project.getName()),
                () -> assertEquals("TF-API-2", project.getProjectKey()),
                () -> assertEquals("核心接口项目", project.getDescription()),
                () -> assertEquals(ProjectStatus.ACTIVE, project.getStatus()),
                () -> assertEquals("u001", project.getCreatedBy()),
                () -> assertEquals(0, project.getVersion()),
                () -> assertEquals(CREATED_AT, project.getCreatedAt()),
                () -> assertEquals(CREATED_AT, project.getUpdatedAt())
        );
    }

    @Test
    void shouldNormalizeBlankDescriptionToNull() {
        Project project = Project.create(
                "p001",
                "tm001",
                "TeamFlow 服务端",
                "TF-API",
                "   ",
                "u001",
                CREATED_AT
        );

        assertNull(project.getDescription());
    }

    @Test
    void shouldUpdateAllowedFieldsVersionAndTimeTogether() {
        Project project = newProject();

        boolean changed = project.updateDetails(
                " TeamFlow API ",
                " 已完成第一版 ",
                ProjectStatus.ARCHIVED,
                CHANGED_AT
        );

        assertAll(
                () -> assertTrue(changed),
                () -> assertEquals("TeamFlow API", project.getName()),
                () -> assertEquals("已完成第一版", project.getDescription()),
                () -> assertEquals(ProjectStatus.ARCHIVED, project.getStatus()),
                () -> assertEquals(1, project.getVersion()),
                () -> assertEquals(CHANGED_AT, project.getUpdatedAt()),
                () -> assertEquals("TF-API", project.getProjectKey()),
                () -> assertEquals("tm001", project.getTeamId()),
                () -> assertEquals("u001", project.getCreatedBy()),
                () -> assertEquals(CREATED_AT, project.getCreatedAt())
        );
    }

    @Test
    void shouldTreatNormalizedSameDetailsAsNoOp() {
        Project project = newProject();

        boolean changed = project.updateDetails(
                " TeamFlow 服务端 ",
                " 核心接口项目 ",
                ProjectStatus.ACTIVE,
                CHANGED_AT
        );

        assertAll(
                () -> assertFalse(changed),
                () -> assertEquals(0, project.getVersion()),
                () -> assertEquals(CREATED_AT, project.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectInvalidPersistentState() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredProject(
                                "TF-API", -1, CREATED_AT, CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredProject(
                                "tf-api", 0, CREATED_AT, CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredProject(
                                "TF--API", 0, CREATED_AT, CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredProject(
                                "TF-API", 0,
                                "2026-09-15T01:02:03Z",
                                CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> restoredProject(
                                "TF-API", 0,
                                CREATED_AT,
                                "2026-09-15T01:02:03.455Z"
                        )
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new Project(
                                "p001", "tm001", "TeamFlow 服务端",
                                "TF-API", "核心接口项目", null,
                                "u001", 0, CREATED_AT, CREATED_AT
                        )
                )
        );
    }

    @Test
    void shouldRejectEarlierUpdateWithoutChangingState() {
        Project project = newProject();

        assertThrows(
                IllegalArgumentException.class,
                () -> project.updateDetails(
                        "TeamFlow API",
                        null,
                        ProjectStatus.ARCHIVED,
                        "2026-09-15T01:02:03.455Z"
                )
        );

        assertAll(
                () -> assertEquals("TeamFlow 服务端", project.getName()),
                () -> assertEquals("核心接口项目", project.getDescription()),
                () -> assertEquals(ProjectStatus.ACTIVE, project.getStatus()),
                () -> assertEquals(0, project.getVersion()),
                () -> assertEquals(CREATED_AT, project.getUpdatedAt())
        );
    }

    @Test
    void shouldKeepStateWhenVersionOverflows() {
        Project project = restoredProject(
                "TF-API",
                Integer.MAX_VALUE,
                CREATED_AT,
                CREATED_AT
        );

        assertThrows(
                IllegalStateException.class,
                () -> project.updateDetails(
                        "TeamFlow API",
                        null,
                        ProjectStatus.ARCHIVED,
                        CHANGED_AT
                )
        );

        assertAll(
                () -> assertEquals("TeamFlow 服务端", project.getName()),
                () -> assertEquals("核心接口项目", project.getDescription()),
                () -> assertEquals(ProjectStatus.ACTIVE, project.getStatus()),
                () -> assertEquals(Integer.MAX_VALUE, project.getVersion()),
                () -> assertEquals(CREATED_AT, project.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectMissingOrBlankRequiredIdentity() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> Project.create(
                                null, "tm001", "项目", "AB",
                                null, "u001", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Project.create(
                                "p001", " ", "项目", "AB",
                                null, "u001", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Project.create(
                                "p001", "tm001", " ", "AB",
                                null, "u001", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Project.create(
                                "p001", "tm001", "项目", "A",
                                null, "u001", CREATED_AT
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Project.create(
                                "p001", "tm001", "项目", "AB",
                                null, " ", CREATED_AT
                        )
                )
        );
    }

    private Project newProject() {
        return Project.create(
                "p001",
                "tm001",
                "TeamFlow 服务端",
                "TF-API",
                "核心接口项目",
                "u001",
                CREATED_AT
        );
    }

    private Project restoredProject(
            String projectKey,
            int version,
            String createdAt,
            String updatedAt
    ) {
        return new Project(
                "p001",
                "tm001",
                "TeamFlow 服务端",
                projectKey,
                "核心接口项目",
                ProjectStatus.ACTIVE,
                "u001",
                version,
                createdAt,
                updatedAt
        );
    }
}
