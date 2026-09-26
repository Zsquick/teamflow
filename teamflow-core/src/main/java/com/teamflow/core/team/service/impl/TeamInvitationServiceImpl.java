package com.teamflow.core.team.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.auth.support.LoginIdentity;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.team.domain.Team;
import com.teamflow.core.team.domain.TeamInvitation;
import com.teamflow.core.team.domain.TeamInvitationStatus;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.dto.CreateTeamInvitationRequest;
import com.teamflow.core.team.dto.InvitationPreviewRequest;
import com.teamflow.core.team.dto.InvitationUserPreviewResponse;
import com.teamflow.core.team.dto.TeamInvitationResponse;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.event.TeamInvitationAction;
import com.teamflow.core.team.event.TeamInvitationChanged;
import com.teamflow.core.team.mapper.TeamInvitationMapper;
import com.teamflow.core.team.mapper.TeamMapper;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.team.service.TeamAuthorizationService;
import com.teamflow.core.team.service.TeamInvitationEventPublisher;
import com.teamflow.core.team.service.TeamInvitationService;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.mapper.UserMapper;
import com.teamflow.core.user.support.UserIdentityMasker;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 精确查找、发送、响应和撤销团队邀请的默认实现。 */
@Service
public class TeamInvitationServiceImpl implements TeamInvitationService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final TeamInvitationMapper invitationMapper;
    private final UserMapper userMapper;
    private final TeamAuthorizationService authorizationService;
    private final TeamInvitationEventPublisher eventPublisher;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;

    public TeamInvitationServiceImpl(
            TeamMapper teamMapper,
            TeamMemberMapper memberMapper,
            TeamInvitationMapper invitationMapper,
            UserMapper userMapper,
            TeamAuthorizationService authorizationService,
            TeamInvitationEventPublisher eventPublisher,
            ReadableIdGenerator idGenerator,
            Clock clock
    ) {
        this.teamMapper = Objects.requireNonNull(teamMapper);
        this.memberMapper = Objects.requireNonNull(memberMapper);
        this.invitationMapper = Objects.requireNonNull(invitationMapper);
        this.userMapper = Objects.requireNonNull(userMapper);
        this.authorizationService = Objects.requireNonNull(authorizationService);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationUserPreviewResponse preview(
            String currentUserId,
            String teamId,
            InvitationPreviewRequest request
    ) {
        Objects.requireNonNull(request, "邀请预览请求不能为 null");
        TeamMember operator = authorizationService.requireMember(teamId, currentUserId);
        if (!operator.getRole().isAtLeast(TeamRole.ADMIN)) {
            throw new BusinessException(TeamErrorCode.INSUFFICIENT_PERMISSION);
        }
        User candidate = findEligibleCandidate(
                currentUserId,
                teamId,
                request.identifier()
        );
        return new InvitationUserPreviewResponse(
                candidate.getDisplayName(),
                UserIdentityMasker.maskUsername(candidate.getUsername()),
                UserIdentityMasker.maskEmail(candidate.getEmail()),
                assignableRoles(operator.getRole())
        );
    }

    @Override
    @Transactional
    public TeamInvitationResponse send(
            String currentUserId,
            String teamId,
            CreateTeamInvitationRequest request
    ) {
        Objects.requireNonNull(request, "发送邀请请求不能为 null");
        Team team = lockTeam(teamId);
        TeamRole operatorRole = authorizationService.requireAtLeast(
                teamId, currentUserId, TeamRole.ADMIN);
        requireAssignable(operatorRole, request.role());
        User candidate = findEligibleCandidate(
                currentUserId, teamId, request.identifier());
        String now = UtcTimeText.now(clock);
        TeamInvitation invitation = TeamInvitation.create(
                idGenerator.nextId(ResourceType.TEAM_INVITATION),
                teamId,
                currentUserId,
                candidate.getId(),
                request.role(),
                now
        );
        requireSingleInsert(invitationMapper.insert(invitation), "团队邀请");
        publish(invitation, team, candidate, currentUserId,
                candidate.getId(), TeamInvitationAction.CREATED, now);
        return invitationMapper.findDetailsById(invitation.getId())
                .map(TeamInvitationResponse::from)
                .orElseThrow(() -> new IllegalStateException("新建邀请无法读回"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> listReceived(String currentUserId) {
        return invitationMapper.findDetailsByInvitee(currentUserId).stream()
                .map(TeamInvitationResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamInvitationResponse> listByTeam(
            String currentUserId,
            String teamId
    ) {
        TeamMember operator = authorizationService.requireMember(teamId, currentUserId);
        if (!operator.getRole().isAtLeast(TeamRole.ADMIN)) {
            throw new BusinessException(TeamErrorCode.INSUFFICIENT_PERMISSION);
        }
        return invitationMapper.findDetailsByTeam(teamId).stream()
                .map(TeamInvitationResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void accept(String currentUserId, String invitationId) {
        TeamInvitation snapshot = requireVisibleInvitation(invitationId, currentUserId);
        Team team = lockTeam(snapshot.getTeamId());
        TeamRole inviterRole;
        try {
            inviterRole = authorizationService.requireAtLeast(
                    snapshot.getTeamId(), snapshot.getInviterId(), TeamRole.ADMIN);
        } catch (BusinessException exception) {
            throw new BusinessException(TeamErrorCode.INVITATION_NO_LONGER_VALID);
        }
        TeamInvitation invitation = lockVisibleInvitation(invitationId, currentUserId);
        if (!inviterRole.canAssign(invitation.getInvitedRole())
                || memberMapper.findByTeamAndUser(
                        invitation.getTeamId(), currentUserId).isPresent()) {
            throw new BusinessException(TeamErrorCode.INVITATION_NO_LONGER_VALID);
        }

        User invitee = requireUser(currentUserId);
        String now = UtcTimeText.now(clock);
        TeamMember member = TeamMember.create(
                idGenerator.nextId(ResourceType.TEAM_MEMBER),
                invitation.getTeamId(),
                currentUserId,
                invitation.getInvitedRole(),
                now
        );
        try {
            requireSingleInsert(memberMapper.insert(member), "团队成员关系");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(TeamErrorCode.INVITATION_NO_LONGER_VALID);
        }
        int expectedVersion = invitation.getVersion();
        invitation.accept(now);
        requireInvitationChanged(invitation, expectedVersion);
        publish(invitation, team, invitee, currentUserId,
                invitation.getInviterId(), TeamInvitationAction.ACCEPTED, now);
    }

    @Override
    @Transactional
    public void reject(String currentUserId, String invitationId) {
        TeamInvitation snapshot = requireVisibleInvitation(invitationId, currentUserId);
        Team team = lockTeam(snapshot.getTeamId());
        TeamInvitation invitation = lockVisibleInvitation(invitationId, currentUserId);
        User invitee = requireUser(currentUserId);
        String now = UtcTimeText.now(clock);
        int expectedVersion = invitation.getVersion();
        invitation.reject(now);
        requireInvitationChanged(invitation, expectedVersion);
        publish(invitation, team, invitee, currentUserId,
                invitation.getInviterId(), TeamInvitationAction.REJECTED, now);
    }

    @Override
    @Transactional
    public void revoke(
            String currentUserId,
            String teamId,
            String invitationId
    ) {
        TeamInvitation snapshot = invitationMapper.findById(invitationId)
                .filter(value -> value.getTeamId().equals(teamId))
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.INVITATION_NOT_FOUND));
        Team team = lockTeam(teamId);
        TeamRole operatorRole = authorizationService.requireAtLeast(
                teamId, currentUserId, TeamRole.ADMIN);
        requireAssignable(operatorRole, snapshot.getInvitedRole());
        TeamInvitation invitation = invitationMapper.findByIdForUpdate(invitationId)
                .filter(value -> value.getTeamId().equals(teamId))
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.INVITATION_NOT_FOUND));
        User invitee = requireUser(invitation.getInviteeId());
        String now = UtcTimeText.now(clock);
        int expectedVersion = invitation.getVersion();
        invitation.revoke(currentUserId, now);
        requireInvitationChanged(invitation, expectedVersion);
        publish(invitation, team, invitee, currentUserId,
                invitation.getInviterId(), TeamInvitationAction.REVOKED, now);
        publish(invitation, team, invitee, currentUserId,
                invitation.getInviteeId(), TeamInvitationAction.REVOKED, now);
    }

    private User findEligibleCandidate(
            String currentUserId,
            String teamId,
            String identifier
    ) {
        User candidate = userMapper.findByIdentifier(
                        LoginIdentity.normalize(identifier))
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.USER_NOT_FOUND));
        if (!candidate.getStatus().canAuthenticate()) {
            throw new BusinessException(TeamErrorCode.INVITATION_TARGET_INVALID);
        }
        if (candidate.getId().equals(currentUserId)) {
            throw new BusinessException(TeamErrorCode.SELF_INVITATION_NOT_ALLOWED);
        }
        if (memberMapper.findByTeamAndUser(teamId, candidate.getId()).isPresent()) {
            throw new BusinessException(TeamErrorCode.MEMBER_ALREADY_EXISTS);
        }
        if (invitationMapper.findPendingByTeamAndInvitee(
                teamId, candidate.getId()).isPresent()) {
            throw new BusinessException(TeamErrorCode.INVITATION_ALREADY_PENDING);
        }
        return candidate;
    }

    private TeamInvitation requireVisibleInvitation(
            String invitationId,
            String currentUserId
    ) {
        return invitationMapper.findById(invitationId)
                .filter(value -> value.getInviteeId().equals(currentUserId))
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.INVITATION_NOT_FOUND));
    }

    private TeamInvitation lockVisibleInvitation(
            String invitationId,
            String currentUserId
    ) {
        return invitationMapper.findByIdForUpdate(invitationId)
                .filter(value -> value.getInviteeId().equals(currentUserId))
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.INVITATION_NOT_FOUND));
    }

    private Team lockTeam(String teamId) {
        return teamMapper.findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.TEAM_NOT_FOUND));
    }

    private User requireUser(String userId) {
        return userMapper.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.USER_NOT_FOUND));
    }

    private void publish(
            TeamInvitation invitation,
            Team team,
            User invitee,
            String operatorId,
            String recipientId,
            TeamInvitationAction action,
            String occurredAt
    ) {
        eventPublisher.publish(new TeamInvitationChanged(
                action,
                invitation.getId(),
                invitation.getTeamId(),
                team.getName(),
                invitation.getInvitedRole(),
                invitation.getStatus(),
                invitation.getInviterId(),
                invitation.getInviteeId(),
                invitee.getDisplayName(),
                recipientId,
                operatorId,
                invitation.getVersion(),
                occurredAt
        ));
    }

    private void requireInvitationChanged(
            TeamInvitation invitation,
            int expectedVersion
    ) {
        if (invitationMapper.updateState(
                invitation,
                TeamInvitationStatus.PENDING,
                expectedVersion
        ) != 1) {
            throw new BusinessException(TeamErrorCode.INVITATION_CHANGE_CONFLICT);
        }
    }

    private static List<TeamRole> assignableRoles(TeamRole operatorRole) {
        return Arrays.stream(TeamRole.values())
                .filter(operatorRole::canAssign)
                .toList();
    }

    private static void requireAssignable(TeamRole operatorRole, TeamRole role) {
        if (!operatorRole.canAssign(role)) {
            throw new BusinessException(TeamErrorCode.ROLE_NOT_ASSIGNABLE);
        }
    }

    private static void requireSingleInsert(int affectedRows, String resource) {
        if (affectedRows != 1) {
            throw new IllegalStateException("新增" + resource + "时受影响行数必须为 1");
        }
    }
}
