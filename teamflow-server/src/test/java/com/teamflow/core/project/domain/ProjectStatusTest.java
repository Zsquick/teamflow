package com.teamflow.core.project.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 项目生命周期状态迁移规则测试。 */
class ProjectStatusTest {

    @Test
    void shouldAllowIdempotentTransitionsAndArchivingActiveProject() {
        assertAll(
                () -> assertTrue(
                        ProjectStatus.ACTIVE.canTransitionTo(ProjectStatus.ACTIVE)
                ),
                () -> assertTrue(
                        ProjectStatus.ACTIVE.canTransitionTo(ProjectStatus.ARCHIVED)
                ),
                () -> assertTrue(
                        ProjectStatus.ARCHIVED.canTransitionTo(ProjectStatus.ARCHIVED)
                )
        );
    }

    @Test
    void shouldNotReactivateArchivedProject() {
        assertFalse(
                ProjectStatus.ARCHIVED.canTransitionTo(ProjectStatus.ACTIVE)
        );
    }

    @Test
    void shouldRejectMissingTargetStatus() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> ProjectStatus.ACTIVE.canTransitionTo(null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> ProjectStatus.ARCHIVED.canTransitionTo(null)
                )
        );
    }
}
