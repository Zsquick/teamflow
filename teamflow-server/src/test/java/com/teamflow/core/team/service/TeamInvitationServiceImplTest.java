package com.teamflow.core.team.service;

import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.team.domain.Team;
import com.teamflow.core.team.domain.TeamInvitation;
import com.teamflow.core.team.domain.TeamInvitationStatus;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.dto.CreateTeamInvitationRequest;
import com.teamflow.core.team.dto.InvitationPreviewRequest;
import com.teamflow.core.team.dto.TeamInvitationDetails;
import com.teamflow.core.team.dto.TeamInvitationResponse;
import com.teamflow.core.team.event.TeamInvitationAction;
import com.teamflow.core.team.event.TeamInvitationChanged;
import com.teamflow.core.team.mapper.TeamInvitationMapper;
import com.teamflow.core.team.mapper.TeamMapper;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.team.service.impl.TeamInvitationServiceImpl;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamInvitationServiceImplTest {

    private static final String NOW = "2026-09-21T01:02:03.456Z";
    private static final String TEAM_ID = "tm001";
    private static final String INVITER_ID = "u001";
    private static final String INVITEE_ID = "u002";

    @Mock private TeamMapper teamMapper;
    @Mock private TeamMemberMapper memberMapper;
    @Mock private TeamInvitationMapper invitationMapper;
    @Mock private UserMapper userMapper;
    @Mock private TeamAuthorizationService authorizationService;
    @Mock private TeamInvitationEventPublisher eventPublisher;
    @Mock private ReadableIdGenerator idGenerator;

    private TeamInvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeamInvitationServiceImpl(
                teamMapper, memberMapper, invitationMapper, userMapper,
                authorizationService, eventPublisher, idGenerator,
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldPreviewExactUserWithMaskedIdentityAndAssignableRoles() {
        when(authorizationService.requireMember(TEAM_ID, INVITER_ID))
                .thenReturn(TeamMember.create(
                        "mb001", TEAM_ID, INVITER_ID, TeamRole.OWNER, NOW));
        when(userMapper.findByIdentifier("target@example.com"))
                .thenReturn(Optional.of(invitee()));
        when(memberMapper.findByTeamAndUser(TEAM_ID, INVITEE_ID))
                .thenReturn(Optional.empty());
        when(invitationMapper.findPendingByTeamAndInvitee(TEAM_ID, INVITEE_ID))
                .thenReturn(Optional.empty());

        var preview = service.preview(
                INVITER_ID, TEAM_ID,
                new InvitationPreviewRequest(" Target@Example.com ")
        );

        assertEquals("目标用户", preview.displayName());
        assertEquals("t****t", preview.maskedUsername());
        assertEquals("t****t@example.com", preview.maskedEmail());
        assertEquals(java.util.List.of(TeamRole.ADMIN, TeamRole.MEMBER),
                preview.assignableRoles());
    }

    @Test
    void shouldCreatePendingInvitationWithoutAddingMember() {
        prepareSend();

        TeamInvitationResponse response = service.send(
                INVITER_ID, TEAM_ID,
                new CreateTeamInvitationRequest(
                        "target@example.com", TeamRole.MEMBER)
        );

        assertEquals(TeamInvitationStatus.PENDING, response.status());
        verify(memberMapper, never()).insert(any(TeamMember.class));
        ArgumentCaptor<TeamInvitationChanged> event =
                ArgumentCaptor.forClass(TeamInvitationChanged.class);
        verify(eventPublisher).publish(event.capture());
        assertEquals(TeamInvitationAction.CREATED, event.getValue().action());
        assertEquals(INVITEE_ID, event.getValue().recipientId());
    }

    @Test
    void shouldAddMemberOnlyWhenInviteeAccepts() {
        TeamInvitation invitation = pendingInvitation();
        when(invitationMapper.findById("ti001"))
                .thenReturn(Optional.of(invitation));
        when(teamMapper.findByIdForUpdate(TEAM_ID))
                .thenReturn(Optional.of(team()));
        when(authorizationService.requireAtLeast(
                TEAM_ID, INVITER_ID, TeamRole.ADMIN))
                .thenReturn(TeamRole.OWNER);
        when(invitationMapper.findByIdForUpdate("ti001"))
                .thenReturn(Optional.of(invitation));
        when(memberMapper.findByTeamAndUser(TEAM_ID, INVITEE_ID))
                .thenReturn(Optional.empty());
        when(userMapper.findById(INVITEE_ID))
                .thenReturn(Optional.of(invitee()));
        when(idGenerator.nextId(ResourceType.TEAM_MEMBER)).thenReturn("mb002");
        when(memberMapper.insert(any(TeamMember.class))).thenReturn(1);
        when(invitationMapper.updateState(
                any(TeamInvitation.class),
                org.mockito.ArgumentMatchers.eq(TeamInvitationStatus.PENDING),
                org.mockito.ArgumentMatchers.eq(0)
        )).thenReturn(1);

        service.accept(INVITEE_ID, "ti001");

        ArgumentCaptor<TeamMember> member =
                ArgumentCaptor.forClass(TeamMember.class);
        verify(memberMapper).insert(member.capture());
        assertEquals(INVITEE_ID, member.getValue().getUserId());
        assertEquals(TeamRole.MEMBER, member.getValue().getRole());
        assertEquals(TeamInvitationStatus.ACCEPTED, invitation.getStatus());
        ArgumentCaptor<TeamInvitationChanged> event =
                ArgumentCaptor.forClass(TeamInvitationChanged.class);
        verify(eventPublisher).publish(event.capture());
        assertEquals(TeamInvitationAction.ACCEPTED, event.getValue().action());
        assertEquals(INVITER_ID, event.getValue().recipientId());
    }

    private void prepareSend() {
        when(teamMapper.findByIdForUpdate(TEAM_ID))
                .thenReturn(Optional.of(team()));
        when(authorizationService.requireAtLeast(
                TEAM_ID, INVITER_ID, TeamRole.ADMIN))
                .thenReturn(TeamRole.OWNER);
        when(userMapper.findByIdentifier("target@example.com"))
                .thenReturn(Optional.of(invitee()));
        when(memberMapper.findByTeamAndUser(TEAM_ID, INVITEE_ID))
                .thenReturn(Optional.empty());
        when(invitationMapper.findPendingByTeamAndInvitee(TEAM_ID, INVITEE_ID))
                .thenReturn(Optional.empty());
        when(idGenerator.nextId(ResourceType.TEAM_INVITATION))
                .thenReturn("ti001");
        when(invitationMapper.insert(any(TeamInvitation.class))).thenReturn(1);
        when(invitationMapper.findDetailsById("ti001"))
                .thenReturn(Optional.of(details()));
    }

    private static Team team() {
        return new Team(TEAM_ID, "研发团队", null, INVITER_ID,
                0, NOW, NOW);
    }

    private static User invitee() {
        return User.create(INVITEE_ID, "target", "target@example.com",
                "password-hash", "目标用户", NOW);
    }

    private static TeamInvitation pendingInvitation() {
        return TeamInvitation.create("ti001", TEAM_ID, INVITER_ID,
                INVITEE_ID, TeamRole.MEMBER, NOW);
    }

    private static TeamInvitationDetails details() {
        return new TeamInvitationDetails(
                "ti001", TEAM_ID, "研发团队", INVITER_ID, "邀请人",
                INVITEE_ID, "target", "target@example.com", "目标用户",
                TeamRole.MEMBER, TeamInvitationStatus.PENDING,
                0, NOW, NOW, null
        );
    }
}
