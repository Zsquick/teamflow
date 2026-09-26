package com.teamflow.core.team.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 团队角色显式权限层级测试。 */
class TeamRoleTest {

    @Test
    void shouldCompareMinimumRoleWithoutDependingOnEnumOrder() {
        assertAll(
                () -> assertTrue(TeamRole.OWNER.isAtLeast(TeamRole.OWNER)),
                () -> assertTrue(TeamRole.OWNER.isAtLeast(TeamRole.ADMIN)),
                () -> assertTrue(TeamRole.OWNER.isAtLeast(TeamRole.MEMBER)),
                () -> assertFalse(TeamRole.ADMIN.isAtLeast(TeamRole.OWNER)),
                () -> assertTrue(TeamRole.ADMIN.isAtLeast(TeamRole.ADMIN)),
                () -> assertTrue(TeamRole.ADMIN.isAtLeast(TeamRole.MEMBER)),
                () -> assertFalse(TeamRole.MEMBER.isAtLeast(TeamRole.OWNER)),
                () -> assertFalse(TeamRole.MEMBER.isAtLeast(TeamRole.ADMIN)),
                () -> assertTrue(TeamRole.MEMBER.isAtLeast(TeamRole.MEMBER))
        );
    }

    @Test
    void shouldOnlyManageStrictlyLowerRoles() {
        assertAll(
                () -> assertFalse(TeamRole.OWNER.canManage(TeamRole.OWNER)),
                () -> assertTrue(TeamRole.OWNER.canManage(TeamRole.ADMIN)),
                () -> assertTrue(TeamRole.OWNER.canManage(TeamRole.MEMBER)),
                () -> assertFalse(TeamRole.ADMIN.canManage(TeamRole.OWNER)),
                () -> assertFalse(TeamRole.ADMIN.canManage(TeamRole.ADMIN)),
                () -> assertTrue(TeamRole.ADMIN.canManage(TeamRole.MEMBER)),
                () -> assertFalse(TeamRole.MEMBER.canManage(TeamRole.OWNER)),
                () -> assertFalse(TeamRole.MEMBER.canManage(TeamRole.ADMIN)),
                () -> assertFalse(TeamRole.MEMBER.canManage(TeamRole.MEMBER))
        );
    }

    @Test
    void shouldNeverAssignOwnerAndOnlyAssignLowerRoles() {
        assertAll(
                () -> assertFalse(TeamRole.OWNER.canAssign(TeamRole.OWNER)),
                () -> assertTrue(TeamRole.OWNER.canAssign(TeamRole.ADMIN)),
                () -> assertTrue(TeamRole.OWNER.canAssign(TeamRole.MEMBER)),
                () -> assertFalse(TeamRole.ADMIN.canAssign(TeamRole.OWNER)),
                () -> assertFalse(TeamRole.ADMIN.canAssign(TeamRole.ADMIN)),
                () -> assertTrue(TeamRole.ADMIN.canAssign(TeamRole.MEMBER)),
                () -> assertFalse(TeamRole.MEMBER.canAssign(TeamRole.OWNER)),
                () -> assertFalse(TeamRole.MEMBER.canAssign(TeamRole.ADMIN)),
                () -> assertFalse(TeamRole.MEMBER.canAssign(TeamRole.MEMBER))
        );
    }

    @Test
    void shouldRejectMissingRoleArguments() {
        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> TeamRole.OWNER.isAtLeast(null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> TeamRole.OWNER.canManage(null)
                ),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> TeamRole.OWNER.canAssign(null)
                )
        );
    }
}
