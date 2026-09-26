package com.teamflow.core.team.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.team.service.TeamAuthorizationService;
import com.teamflow.core.team.service.TeamManagementContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** 集中执行团队资源级成员身份与角色层级校验。 */
@Service
public class TeamAuthorizationServiceImpl
        implements TeamAuthorizationService {

    private final TeamMemberMapper teamMemberMapper;

    public TeamAuthorizationServiceImpl(
            TeamMemberMapper teamMemberMapper
    ) {
        this.teamMemberMapper = Objects.requireNonNull(
                teamMemberMapper,
                "团队成员 Mapper 不能为 null"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TeamMember requireMember(String teamId, String userId) {
        Objects.requireNonNull(teamId, "团队编号不能为 null");
        Objects.requireNonNull(userId, "用户编号不能为 null");

        return teamMemberMapper.findByTeamAndUser(teamId, userId)
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.TEAM_NOT_FOUND
                ));
    }

    @Override
    @Transactional
    public TeamRole requireAtLeast(
            String teamId,
            String userId,
            TeamRole minimumRole
    ) {
        Objects.requireNonNull(teamId, "团队编号不能为 null");
        Objects.requireNonNull(userId, "用户编号不能为 null");
        Objects.requireNonNull(minimumRole, "最低团队角色不能为 null");

        TeamRole actualRole = teamMemberMapper
                .findByTeamAndUsersForUpdate(teamId, List.of(userId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                    TeamErrorCode.TEAM_NOT_FOUND
                ))
                .getRole();
        if (!actualRole.isAtLeast(minimumRole)) {
            throw new BusinessException(
                    TeamErrorCode.INSUFFICIENT_PERMISSION
            );
        }
        return actualRole;
    }

    @Override
    @Transactional
    public void requireMembersForUpdate(
            String teamId,
            String operatorId,
            String relatedUserId
    ) {
        Objects.requireNonNull(teamId, "团队编号不能为 null");
        Objects.requireNonNull(operatorId, "操作者用户编号不能为 null");

        List<String> lockedUserIds = Stream
                .of(operatorId, relatedUserId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        List<TeamMember> lockedMembers = teamMemberMapper
                .findByTeamAndUsersForUpdate(teamId, lockedUserIds);
        findLockedMember(lockedMembers, operatorId)
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.TEAM_NOT_FOUND
                ));
        if (relatedUserId != null) {
            findLockedMember(lockedMembers, relatedUserId)
                    .orElseThrow(() -> new BusinessException(
                            TeamErrorCode.MEMBER_NOT_FOUND
                    ));
        }
    }

    @Override
    @Transactional
    public TeamManagementContext requireCanManageMember(
            String teamId,
            String operatorId,
            String targetUserId
    ) {
        Objects.requireNonNull(teamId, "团队编号不能为 null");
        Objects.requireNonNull(operatorId, "操作者用户编号不能为 null");
        Objects.requireNonNull(targetUserId, "目标用户编号不能为 null");

        List<String> lockedUserIds = List.of(operatorId, targetUserId)
                .stream()
                .distinct()
                .sorted()
                .toList();
        List<TeamMember> lockedMembers = teamMemberMapper
                .findByTeamAndUsersForUpdate(teamId, lockedUserIds);
        TeamMember operator = findLockedMember(lockedMembers, operatorId)
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.TEAM_NOT_FOUND
                ));
        TeamRole operatorRole = operator.getRole();
        if (!operatorRole.isAtLeast(TeamRole.ADMIN)) {
            throw new BusinessException(
                    TeamErrorCode.INSUFFICIENT_PERMISSION
            );
        }
        if (operatorId.equals(targetUserId)) {
            throw new BusinessException(
                    TeamErrorCode.SELF_MANAGEMENT_NOT_ALLOWED
            );
        }

        TeamMember target = findLockedMember(lockedMembers, targetUserId)
                .orElseThrow(() -> new BusinessException(
                        TeamErrorCode.MEMBER_NOT_FOUND
                ));
        TeamRole targetRole = target.getRole();
        if (targetRole == TeamRole.OWNER) {
            throw new BusinessException(
                    TeamErrorCode.OWNER_ROLE_PROTECTED
            );
        }
        if (!operatorRole.canManage(targetRole)) {
            throw new BusinessException(
                    TeamErrorCode.INSUFFICIENT_PERMISSION
            );
        }

        return new TeamManagementContext(operatorRole, targetRole);
    }

    private static Optional<TeamMember> findLockedMember(
            List<TeamMember> members,
            String userId
    ) {
        return members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst();
    }
}
