package com.teamflow.core.team.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
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
import com.teamflow.core.team.service.TeamAuthorizationService;
import com.teamflow.core.team.service.TeamManagementContext;
import com.teamflow.core.team.service.TeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** 团队创建、成员查询和成员管理的默认业务实现。 */
@Service
public class TeamServiceImpl implements TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamAuthorizationService authorizationService;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;

    public TeamServiceImpl(
            TeamMapper teamMapper,
            TeamMemberMapper teamMemberMapper,
            TeamAuthorizationService authorizationService,
            ReadableIdGenerator idGenerator,
            Clock clock
    ) {
        this.teamMapper = Objects.requireNonNull(
                teamMapper,
                "团队 Mapper 不能为 null"
        );
        this.teamMemberMapper = Objects.requireNonNull(
                teamMemberMapper,
                "团队成员 Mapper 不能为 null"
        );
        this.authorizationService = Objects.requireNonNull(
                authorizationService,
                "团队权限服务不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "项目时钟不能为 null");
    }

    @Override
    @Transactional
    public TeamResponse create(
            String currentUserId,
            CreateTeamRequest request
    ) {
        Objects.requireNonNull(currentUserId, "当前用户编号不能为 null");
        Objects.requireNonNull(request, "创建团队请求不能为 null");

        String teamId = idGenerator.nextId(ResourceType.TEAM);
        String memberId = idGenerator.nextId(ResourceType.TEAM_MEMBER);
        String now = UtcTimeText.now(clock);
        Team team = Team.create(
                teamId,
                request.name(),
                request.description(),
                currentUserId,
                now
        );
        TeamMember owner = TeamMember.create(
                memberId,
                teamId,
                currentUserId,
                TeamRole.OWNER,
                now
        );

        requireSingleInsert(teamMapper.insert(team), "团队");
        requireSingleInsert(teamMemberMapper.insert(owner), "团队所有者关系");
        return TeamResponse.from(team, TeamRole.OWNER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamResponse> listMine(String currentUserId) {
        Objects.requireNonNull(currentUserId, "当前用户编号不能为 null");
        return List.copyOf(teamMapper.findByUserId(currentUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(
            String currentUserId,
            String teamId
    ) {
        authorizationService.requireMember(teamId, currentUserId);
        return List.copyOf(teamMemberMapper.findDetailsByTeamId(teamId));
    }

    @Override
    @Transactional
    public void updateMemberRole(
            String currentUserId,
            String teamId,
            String memberUserId,
            UpdateTeamMemberRoleRequest request
    ) {
        Objects.requireNonNull(request, "修改成员角色请求不能为 null");

        TeamManagementContext context = authorizationService
                .requireCanManageMember(
                        teamId,
                        currentUserId,
                        memberUserId
                );
        TeamRole newRole = request.role();
        if (!context.operatorRole().canAssign(newRole)) {
            throw new BusinessException(
                    TeamErrorCode.ROLE_NOT_ASSIGNABLE
            );
        }
        if (context.targetRole() == newRole) {
            return;
        }

        int affectedRows = teamMemberMapper.updateRole(
                teamId,
                memberUserId,
                context.targetRole(),
                newRole
        );
        requireMemberChanged(affectedRows);
    }

    @Override
    @Transactional
    public void removeMember(
            String currentUserId,
            String teamId,
            String memberUserId
    ) {
        TeamManagementContext context = authorizationService
                .requireCanManageMember(
                        teamId,
                        currentUserId,
                        memberUserId
                );
        int affectedRows = teamMemberMapper.delete(
                teamId,
                memberUserId,
                context.targetRole()
        );
        requireMemberChanged(affectedRows);
    }

    private static void requireSingleInsert(
            int affectedRows,
            String resourceName
    ) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增" + resourceName + "时受影响行数必须为 1"
            );
        }
    }

    private static void requireMemberChanged(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(
                    TeamErrorCode.MEMBER_CHANGE_CONFLICT
            );
        }
    }
}
