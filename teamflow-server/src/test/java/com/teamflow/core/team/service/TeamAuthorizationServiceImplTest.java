package com.teamflow.core.team.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.team.service.impl.TeamAuthorizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 团队成员身份、锁定式角色校验和管理边界测试。 */
@ExtendWith(MockitoExtension.class)
class TeamAuthorizationServiceImplTest {

    private static final String TEAM_ID = "tm001";
    private static final String OPERATOR_ID = "u001";
    private static final String TARGET_ID = "u002";
    private static final String JOINED_AT = "2026-09-12T01:02:03.456Z";

    @Mock
    private TeamMemberMapper teamMemberMapper;

    private TeamAuthorizationServiceImpl authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new TeamAuthorizationServiceImpl(
                teamMemberMapper
        );
    }

    @Test
    void shouldRequireMapperDependency() {
        assertThrows(
                NullPointerException.class,
                () -> new TeamAuthorizationServiceImpl(null)
        );
    }

    @Test
    void shouldReturnExistingMembershipWithOneReadOnlyLookup() {
        TeamMember member = member(OPERATOR_ID, TeamRole.MEMBER);
        when(teamMemberMapper.findByTeamAndUser(TEAM_ID, OPERATOR_ID))
                .thenReturn(Optional.of(member));

        TeamMember result = authorizationService.requireMember(
                TEAM_ID,
                OPERATOR_ID
        );

        assertSame(member, result);
        verify(teamMemberMapper, times(1)).findByTeamAndUser(
                TEAM_ID,
                OPERATOR_ID
        );
    }

    @Test
    void shouldHideTeamExistenceFromNonMember() {
        when(teamMemberMapper.findByTeamAndUser(TEAM_ID, OPERATOR_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireMember(
                        TEAM_ID,
                        OPERATOR_ID
                )
        );

        assertEquals(TeamErrorCode.TEAM_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldLockOperatorAndAcceptOwnerOrAdminForAdminMinimum() {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID)
        )).thenReturn(List.of(member(OPERATOR_ID, TeamRole.OWNER)));

        assertEquals(
                TeamRole.OWNER,
                authorizationService.requireAtLeast(
                        TEAM_ID,
                        OPERATOR_ID,
                        TeamRole.ADMIN
                )
        );

        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID)
        )).thenReturn(List.of(member(OPERATOR_ID, TeamRole.ADMIN)));

        assertEquals(
                TeamRole.ADMIN,
                authorizationService.requireAtLeast(
                        TEAM_ID,
                        OPERATOR_ID,
                        TeamRole.ADMIN
                )
        );
    }

    @Test
    void shouldRejectMissingOrInsufficientLockedOperator() {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID)
        )).thenReturn(List.of());

        BusinessException missing = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireAtLeast(
                        TEAM_ID,
                        OPERATOR_ID,
                        TeamRole.ADMIN
                )
        );
        assertEquals(TeamErrorCode.TEAM_NOT_FOUND, missing.getErrorCode());

        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID)
        )).thenReturn(List.of(member(OPERATOR_ID, TeamRole.MEMBER)));

        BusinessException insufficient = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireAtLeast(
                        TEAM_ID,
                        OPERATOR_ID,
                        TeamRole.ADMIN
                )
        );
        assertEquals(
                TeamErrorCode.INSUFFICIENT_PERMISSION,
                insufficient.getErrorCode()
        );
    }

    @Test
    void shouldRejectMissingMinimumRoleBeforeDatabaseLookup() {
        assertThrows(
                NullPointerException.class,
                () -> authorizationService.requireAtLeast(
                        TEAM_ID,
                        OPERATOR_ID,
                        null
                )
        );
        verifyNoInteractions(teamMemberMapper);
    }

    @Test
    void shouldLockOperatorAndRelatedMemberInStableOrder() {
        String operatorId = "u009";
        String relatedUserId = "u002";
        List<String> sortedIds = List.of(relatedUserId, operatorId);
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                sortedIds
        )).thenReturn(List.of(
                member(relatedUserId, TeamRole.MEMBER),
                member(operatorId, TeamRole.MEMBER)
        ));

        authorizationService.requireMembersForUpdate(
                TEAM_ID,
                operatorId,
                relatedUserId
        );

        verify(teamMemberMapper).findByTeamAndUsersForUpdate(
                TEAM_ID,
                sortedIds
        );
    }

    @Test
    void shouldLockOneRowWhenRelatedMemberIsNullOrSameUser() {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID)
        )).thenReturn(List.of(member(OPERATOR_ID, TeamRole.MEMBER)));

        authorizationService.requireMembersForUpdate(
                TEAM_ID,
                OPERATOR_ID,
                null
        );
        authorizationService.requireMembersForUpdate(
                TEAM_ID,
                OPERATOR_ID,
                OPERATOR_ID
        );

        verify(teamMemberMapper, times(2)).findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID)
        );
    }

    @Test
    void shouldDistinguishMissingOperatorAndRelatedMember() {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID, TARGET_ID)
        )).thenReturn(List.of(member(TARGET_ID, TeamRole.MEMBER)));

        BusinessException missingOperator = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireMembersForUpdate(
                        TEAM_ID,
                        OPERATOR_ID,
                        TARGET_ID
                )
        );
        assertEquals(
                TeamErrorCode.TEAM_NOT_FOUND,
                missingOperator.getErrorCode()
        );

        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID, TARGET_ID)
        )).thenReturn(List.of(member(OPERATOR_ID, TeamRole.MEMBER)));

        BusinessException missingRelated = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireMembersForUpdate(
                        TEAM_ID,
                        OPERATOR_ID,
                        TARGET_ID
                )
        );
        assertEquals(
                TeamErrorCode.MEMBER_NOT_FOUND,
                missingRelated.getErrorCode()
        );
    }

    @Test
    void shouldLockBothMembersOnceInStableUserOrder() {
        String operatorId = "u009";
        String targetId = "u002";
        List<String> sortedIds = List.of(targetId, operatorId);
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                sortedIds
        )).thenReturn(List.of(
                member(targetId, TeamRole.MEMBER),
                member(operatorId, TeamRole.OWNER)
        ));

        TeamManagementContext context = authorizationService
                .requireCanManageMember(TEAM_ID, operatorId, targetId);

        assertEquals(TeamRole.OWNER, context.operatorRole());
        assertEquals(TeamRole.MEMBER, context.targetRole());
        verify(teamMemberMapper, times(1))
                .findByTeamAndUsersForUpdate(TEAM_ID, sortedIds);
    }

    @Test
    void shouldAllowOwnerToManageAdminAndAdminToManageMember() {
        prepareLockedManagement(TeamRole.OWNER, TeamRole.ADMIN);
        TeamManagementContext ownerContext = authorizationService
                .requireCanManageMember(TEAM_ID, OPERATOR_ID, TARGET_ID);
        assertEquals(TeamRole.OWNER, ownerContext.operatorRole());
        assertEquals(TeamRole.ADMIN, ownerContext.targetRole());

        prepareLockedManagement(TeamRole.ADMIN, TeamRole.MEMBER);
        TeamManagementContext adminContext = authorizationService
                .requireCanManageMember(TEAM_ID, OPERATOR_ID, TARGET_ID);
        assertEquals(TeamRole.ADMIN, adminContext.operatorRole());
        assertEquals(TeamRole.MEMBER, adminContext.targetRole());
    }

    @Test
    void shouldRejectMissingOrOrdinaryOperatorBeforeUsingTargetResult() {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID, TARGET_ID)
        )).thenReturn(List.of(member(TARGET_ID, TeamRole.MEMBER)));

        BusinessException missing = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireCanManageMember(
                        TEAM_ID,
                        OPERATOR_ID,
                        TARGET_ID
                )
        );
        assertEquals(TeamErrorCode.TEAM_NOT_FOUND, missing.getErrorCode());

        prepareLockedManagement(TeamRole.MEMBER, TeamRole.MEMBER);
        BusinessException insufficient = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireCanManageMember(
                        TEAM_ID,
                        OPERATOR_ID,
                        TARGET_ID
                )
        );
        assertEquals(
                TeamErrorCode.INSUFFICIENT_PERMISSION,
                insufficient.getErrorCode()
        );
    }

    @Test
    void shouldRejectSelfManagementAfterLockingOneMembership() {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID)
        )).thenReturn(List.of(member(OPERATOR_ID, TeamRole.OWNER)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireCanManageMember(
                        TEAM_ID,
                        OPERATOR_ID,
                        OPERATOR_ID
                )
        );

        assertEquals(
                TeamErrorCode.SELF_MANAGEMENT_NOT_ALLOWED,
                exception.getErrorCode()
        );
    }

    @Test
    void shouldReportMissingTargetOnlyForAuthorizedOperator() {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID, TARGET_ID)
        )).thenReturn(List.of(member(OPERATOR_ID, TeamRole.OWNER)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireCanManageMember(
                        TEAM_ID,
                        OPERATOR_ID,
                        TARGET_ID
                )
        );

        assertEquals(TeamErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void shouldProtectOwnerAndForbidAdminManagingPeer() {
        prepareLockedManagement(TeamRole.ADMIN, TeamRole.OWNER);
        BusinessException ownerProtected = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireCanManageMember(
                        TEAM_ID,
                        OPERATOR_ID,
                        TARGET_ID
                )
        );
        assertEquals(
                TeamErrorCode.OWNER_ROLE_PROTECTED,
                ownerProtected.getErrorCode()
        );

        prepareLockedManagement(TeamRole.ADMIN, TeamRole.ADMIN);
        BusinessException peerForbidden = assertThrows(
                BusinessException.class,
                () -> authorizationService.requireCanManageMember(
                        TEAM_ID,
                        OPERATOR_ID,
                        TARGET_ID
                )
        );
        assertEquals(
                TeamErrorCode.INSUFFICIENT_PERMISSION,
                peerForbidden.getErrorCode()
        );
    }

    private void prepareLockedManagement(
            TeamRole operatorRole,
            TeamRole targetRole
    ) {
        when(teamMemberMapper.findByTeamAndUsersForUpdate(
                TEAM_ID,
                List.of(OPERATOR_ID, TARGET_ID)
        )).thenReturn(List.of(
                member(OPERATOR_ID, operatorRole),
                member(TARGET_ID, targetRole)
        ));
    }

    private TeamMember member(String userId, TeamRole role) {
        return TeamMember.create(
                "mb-" + userId,
                TEAM_ID,
                userId,
                role,
                JOINED_AT
        );
    }
}
