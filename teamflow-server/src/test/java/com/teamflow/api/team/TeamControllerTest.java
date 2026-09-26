package com.teamflow.api.team;

import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.dto.CreateTeamRequest;
import com.teamflow.core.team.dto.TeamMemberResponse;
import com.teamflow.core.team.dto.TeamResponse;
import com.teamflow.core.team.dto.UpdateTeamMemberRoleRequest;
import com.teamflow.core.team.service.TeamService;
import com.teamflow.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 团队 HTTP 接口的认证身份委托与统一响应测试。 */
@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    private static final String NOW = "2026-09-12T01:02:03.456Z";
    private static final String TEAM_ID = "tm001";
    private static final String CURRENT_USER_ID = "u001";
    private static final String TARGET_USER_ID = "u002";

    @Mock
    private TeamService teamService;

    private TeamController controller;
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        controller = new TeamController(teamService);
        currentUser = new AuthenticatedUser(
                CURRENT_USER_ID,
                "zhou",
                Set.of("ROLE_USER")
        );
    }

    @Test
    void shouldRequireTeamService() {
        assertThrows(
                NullPointerException.class,
                () -> new TeamController(null)
        );
    }

    @Test
    void shouldCreateTeamForAuthenticatedUser() {
        CreateTeamRequest request = new CreateTeamRequest(
                "研发团队",
                "TeamFlow 研发"
        );
        TeamResponse expected = teamResponse(TeamRole.OWNER);
        when(teamService.create(CURRENT_USER_ID, request))
                .thenReturn(expected);

        ApiResponse<TeamResponse> response = controller.create(
                currentUser,
                request
        );

        assertEquals("COMMON_0000", response.code());
        assertEquals(expected, response.data());
        verify(teamService).create(CURRENT_USER_ID, request);
    }

    @Test
    void shouldListOnlyAuthenticatedUsersTeams() {
        List<TeamResponse> expected = List.of(
                teamResponse(TeamRole.ADMIN)
        );
        when(teamService.listMine(CURRENT_USER_ID)).thenReturn(expected);

        ApiResponse<List<TeamResponse>> response = controller.listMine(
                currentUser
        );

        assertEquals("COMMON_0000", response.code());
        assertEquals(expected, response.data());
        verify(teamService).listMine(CURRENT_USER_ID);
    }

    @Test
    void shouldListMembersWithAuthenticatedUserAndStringTeamId() {
        List<TeamMemberResponse> expected = List.of(memberResponse());
        when(teamService.listMembers(CURRENT_USER_ID, TEAM_ID))
                .thenReturn(expected);

        ApiResponse<List<TeamMemberResponse>> response =
                controller.listMembers(currentUser, TEAM_ID);

        assertEquals("COMMON_0000", response.code());
        assertEquals(expected, response.data());
        verify(teamService).listMembers(CURRENT_USER_ID, TEAM_ID);
    }

    @Test
    void shouldUpdateTargetRoleAndReturnEmptySuccessData() {
        UpdateTeamMemberRoleRequest request =
                new UpdateTeamMemberRoleRequest(TeamRole.ADMIN);

        ApiResponse<Void> response = controller.updateMemberRole(
                currentUser,
                TEAM_ID,
                TARGET_USER_ID,
                request
        );

        assertEquals("COMMON_0000", response.code());
        assertNull(response.data());
        verify(teamService).updateMemberRole(
                CURRENT_USER_ID,
                TEAM_ID,
                TARGET_USER_ID,
                request
        );
    }

    @Test
    void shouldRemoveTargetMemberAndReturnEmptySuccessData() {
        ApiResponse<Void> response = controller.removeMember(
                currentUser,
                TEAM_ID,
                TARGET_USER_ID
        );

        assertEquals("COMMON_0000", response.code());
        assertNull(response.data());
        verify(teamService).removeMember(
                CURRENT_USER_ID,
                TEAM_ID,
                TARGET_USER_ID
        );
    }

    private TeamResponse teamResponse(TeamRole currentRole) {
        return new TeamResponse(
                TEAM_ID,
                "研发团队",
                "TeamFlow 研发",
                CURRENT_USER_ID,
                0,
                NOW,
                NOW,
                currentRole
        );
    }

    private TeamMemberResponse memberResponse() {
        return new TeamMemberResponse(
                TARGET_USER_ID,
                "target",
                "目标用户",
                null,
                TeamRole.MEMBER,
                NOW
        );
    }
}
