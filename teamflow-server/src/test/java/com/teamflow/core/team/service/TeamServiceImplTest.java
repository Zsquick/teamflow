package com.teamflow.core.team.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.team.domain.Team;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.dto.CreateTeamRequest;
import com.teamflow.core.team.dto.TeamMemberResponse;
import com.teamflow.core.team.dto.TeamResponse;
import com.teamflow.core.team.dto.UpdateTeamMemberRoleRequest;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.mapper.TeamMapper;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.team.service.impl.TeamServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 团队创建、查询和成员管理业务测试。 */
@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    private static final String NOW = "2026-09-12T01:02:03.456Z";
    private static final String TEAM_ID = "tm001";
    private static final String MEMBER_ID = "mb001";
    private static final String OWNER_ID = "u001";
    private static final String TARGET_ID = "u002";

    @Mock
    private TeamMapper teamMapper;
    @Mock
    private TeamMemberMapper teamMemberMapper;
    @Mock
    private TeamAuthorizationService authorizationService;
    @Mock
    private ReadableIdGenerator idGenerator;

    private TeamServiceImpl teamService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
        teamService = new TeamServiceImpl(
                teamMapper,
                teamMemberMapper,
                authorizationService,
                idGenerator,
                clock
        );
    }

    @Test
    void shouldCreateTeamAndOwnerMembershipInOneOrderedFlow() {
        prepareCreateIds();
        when(teamMapper.insert(any(Team.class))).thenReturn(1);
        when(teamMemberMapper.insert(any(TeamMember.class))).thenReturn(1);

        TeamResponse response = teamService.create(
                OWNER_ID,
                new CreateTeamRequest(" 研发团队 ", " 协作项目 ")
        );

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(
                TeamMember.class
        );
        verify(teamMapper).insert(teamCaptor.capture());
        verify(teamMemberMapper).insert(memberCaptor.capture());
        Team savedTeam = teamCaptor.getValue();
        TeamMember savedOwner = memberCaptor.getValue();

        assertAll(
                () -> assertEquals(TEAM_ID, savedTeam.getId()),
                () -> assertEquals("研发团队", savedTeam.getName()),
                () -> assertEquals("协作项目", savedTeam.getDescription()),
                () -> assertEquals(OWNER_ID, savedTeam.getOwnerId()),
                () -> assertEquals(0, savedTeam.getVersion()),
                () -> assertEquals(NOW, savedTeam.getCreatedAt()),
                () -> assertEquals(NOW, savedTeam.getUpdatedAt()),
                () -> assertEquals(MEMBER_ID, savedOwner.getId()),
                () -> assertEquals(TEAM_ID, savedOwner.getTeamId()),
                () -> assertEquals(OWNER_ID, savedOwner.getUserId()),
                () -> assertEquals(TeamRole.OWNER, savedOwner.getRole()),
                () -> assertEquals(NOW, savedOwner.getJoinedAt()),
                () -> assertEquals(TEAM_ID, response.id()),
                () -> assertEquals(TeamRole.OWNER, response.currentUserRole())
        );

        InOrder order = inOrder(idGenerator, teamMapper, teamMemberMapper);
        order.verify(idGenerator).nextId(ResourceType.TEAM);
        order.verify(idGenerator).nextId(ResourceType.TEAM_MEMBER);
        order.verify(teamMapper).insert(savedTeam);
        order.verify(teamMemberMapper).insert(savedOwner);
    }

    @Test
    void shouldStopBeforeOwnerInsertWhenTeamInsertIsAbnormal() {
        prepareCreateIds();
        when(teamMapper.insert(any(Team.class))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> teamService.create(
                        OWNER_ID,
                        new CreateTeamRequest("研发团队", null)
                )
        );

        verify(teamMemberMapper, never()).insert(any(TeamMember.class));
    }

    @Test
    void shouldFailWhenOwnerMembershipInsertIsAbnormal() {
        prepareCreateIds();
        when(teamMapper.insert(any(Team.class))).thenReturn(1);
        when(teamMemberMapper.insert(any(TeamMember.class))).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> teamService.create(
                        OWNER_ID,
                        new CreateTeamRequest("研发团队", null)
                )
        );
    }

    @Test
    void shouldListOnlyCurrentUsersTeamsAsImmutableSnapshot() {
        TeamResponse response = teamResponse(TeamRole.ADMIN);
        List<TeamResponse> mapperResult = new ArrayList<>(List.of(response));
        when(teamMapper.findByUserId(OWNER_ID)).thenReturn(mapperResult);

        List<TeamResponse> result = teamService.listMine(OWNER_ID);
        mapperResult.clear();

        assertEquals(List.of(response), result);
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.add(response)
        );
        verify(teamMapper).findByUserId(OWNER_ID);
    }

    @Test
    void shouldAuthorizeBeforeLoadingMemberDetails() {
        TeamMember currentMember = member(OWNER_ID, TeamRole.MEMBER);
        TeamMemberResponse detail = memberResponse(TARGET_ID, TeamRole.ADMIN);
        when(authorizationService.requireMember(TEAM_ID, OWNER_ID))
                .thenReturn(currentMember);
        when(teamMemberMapper.findDetailsByTeamId(TEAM_ID))
                .thenReturn(List.of(detail));

        List<TeamMemberResponse> result = teamService.listMembers(
                OWNER_ID,
                TEAM_ID
        );

        assertEquals(List.of(detail), result);
        InOrder order = inOrder(authorizationService, teamMemberMapper);
        order.verify(authorizationService).requireMember(TEAM_ID, OWNER_ID);
        order.verify(teamMemberMapper).findDetailsByTeamId(TEAM_ID);
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.add(detail)
        );
    }

    @Test
    void shouldNotLoadMemberDetailsWhenMembershipIsRejected() {
        doThrow(new BusinessException(TeamErrorCode.TEAM_NOT_FOUND))
                .when(authorizationService)
                .requireMember(TEAM_ID, OWNER_ID);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teamService.listMembers(OWNER_ID, TEAM_ID)
        );

        assertEquals(TeamErrorCode.TEAM_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(teamMemberMapper);
    }

    @Test
    void shouldUpdateRoleWithRoleObservedDuringAuthorization() {
        when(authorizationService.requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        )).thenReturn(new TeamManagementContext(
                TeamRole.OWNER,
                TeamRole.ADMIN
        ));
        when(teamMemberMapper.updateRole(
                TEAM_ID,
                TARGET_ID,
                TeamRole.ADMIN,
                TeamRole.MEMBER
        )).thenReturn(1);

        teamService.updateMemberRole(
                OWNER_ID,
                TEAM_ID,
                TARGET_ID,
                new UpdateTeamMemberRoleRequest(TeamRole.MEMBER)
        );

        verify(teamMemberMapper).updateRole(
                TEAM_ID,
                TARGET_ID,
                TeamRole.ADMIN,
                TeamRole.MEMBER
        );
    }

    @Test
    void shouldSkipDatabaseUpdateWhenRoleIsUnchanged() {
        when(authorizationService.requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        )).thenReturn(new TeamManagementContext(
                TeamRole.OWNER,
                TeamRole.ADMIN
        ));

        teamService.updateMemberRole(
                OWNER_ID,
                TEAM_ID,
                TARGET_ID,
                new UpdateTeamMemberRoleRequest(TeamRole.ADMIN)
        );

        verify(teamMemberMapper, never()).updateRole(
                TEAM_ID,
                TARGET_ID,
                TeamRole.ADMIN,
                TeamRole.ADMIN
        );
    }

    @Test
    void shouldRejectAdminPromotionAndOwnerAssignmentBeforeUpdate() {
        when(authorizationService.requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        )).thenReturn(new TeamManagementContext(
                TeamRole.ADMIN,
                TeamRole.MEMBER
        ));

        BusinessException adminException = assertThrows(
                BusinessException.class,
                () -> teamService.updateMemberRole(
                        OWNER_ID,
                        TEAM_ID,
                        TARGET_ID,
                        new UpdateTeamMemberRoleRequest(TeamRole.ADMIN)
                )
        );
        assertEquals(
                TeamErrorCode.ROLE_NOT_ASSIGNABLE,
                adminException.getErrorCode()
        );

        when(authorizationService.requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        )).thenReturn(new TeamManagementContext(
                TeamRole.OWNER,
                TeamRole.MEMBER
        ));

        BusinessException ownerException = assertThrows(
                BusinessException.class,
                () -> teamService.updateMemberRole(
                        OWNER_ID,
                        TEAM_ID,
                        TARGET_ID,
                        new UpdateTeamMemberRoleRequest(TeamRole.OWNER)
                )
        );
        assertEquals(
                TeamErrorCode.ROLE_NOT_ASSIGNABLE,
                ownerException.getErrorCode()
        );
        verifyNoInteractions(teamMemberMapper);
    }

    @Test
    void shouldReportConcurrentRoleChangeWhenExpectedRoleNoLongerMatches() {
        when(authorizationService.requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        )).thenReturn(new TeamManagementContext(
                TeamRole.OWNER,
                TeamRole.ADMIN
        ));
        when(teamMemberMapper.updateRole(
                TEAM_ID,
                TARGET_ID,
                TeamRole.ADMIN,
                TeamRole.MEMBER
        )).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teamService.updateMemberRole(
                        OWNER_ID,
                        TEAM_ID,
                        TARGET_ID,
                        new UpdateTeamMemberRoleRequest(TeamRole.MEMBER)
                )
        );

        assertEquals(
                TeamErrorCode.MEMBER_CHANGE_CONFLICT,
                exception.getErrorCode()
        );
    }

    @Test
    void shouldNotUpdateWhenManagementAuthorizationFails() {
        doThrow(new BusinessException(
                TeamErrorCode.INSUFFICIENT_PERMISSION
        )).when(authorizationService).requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teamService.updateMemberRole(
                        OWNER_ID,
                        TEAM_ID,
                        TARGET_ID,
                        new UpdateTeamMemberRoleRequest(TeamRole.MEMBER)
                )
        );

        assertEquals(
                TeamErrorCode.INSUFFICIENT_PERMISSION,
                exception.getErrorCode()
        );
        verifyNoInteractions(teamMemberMapper);
    }

    @Test
    void shouldDeleteMemberWithRoleObservedDuringAuthorization() {
        when(authorizationService.requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        )).thenReturn(new TeamManagementContext(
                TeamRole.OWNER,
                TeamRole.ADMIN
        ));
        when(teamMemberMapper.delete(
                TEAM_ID,
                TARGET_ID,
                TeamRole.ADMIN
        )).thenReturn(1);

        teamService.removeMember(OWNER_ID, TEAM_ID, TARGET_ID);

        verify(teamMemberMapper).delete(
                TEAM_ID,
                TARGET_ID,
                TeamRole.ADMIN
        );
    }

    @Test
    void shouldReportConcurrentRoleChangeBeforeDelete() {
        when(authorizationService.requireCanManageMember(
                TEAM_ID,
                OWNER_ID,
                TARGET_ID
        )).thenReturn(new TeamManagementContext(
                TeamRole.ADMIN,
                TeamRole.MEMBER
        ));
        when(teamMemberMapper.delete(
                TEAM_ID,
                TARGET_ID,
                TeamRole.MEMBER
        )).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> teamService.removeMember(
                        OWNER_ID,
                        TEAM_ID,
                        TARGET_ID
                )
        );

        assertEquals(
                TeamErrorCode.MEMBER_CHANGE_CONFLICT,
                exception.getErrorCode()
        );
    }

    private void prepareCreateIds() {
        when(idGenerator.nextId(ResourceType.TEAM)).thenReturn(TEAM_ID);
        when(idGenerator.nextId(ResourceType.TEAM_MEMBER)).thenReturn(MEMBER_ID);
    }

    private TeamResponse teamResponse(TeamRole currentRole) {
        return new TeamResponse(
                TEAM_ID,
                "研发团队",
                null,
                OWNER_ID,
                0,
                NOW,
                NOW,
                currentRole
        );
    }

    private TeamMemberResponse memberResponse(
            String userId,
            TeamRole role
    ) {
        return new TeamMemberResponse(
                userId,
                "user_" + userId,
                "成员",
                null,
                role,
                NOW
        );
    }

    private TeamMember member(String userId, TeamRole role) {
        return TeamMember.create(
                "mb-" + userId,
                TEAM_ID,
                userId,
                role,
                NOW
        );
    }

}
