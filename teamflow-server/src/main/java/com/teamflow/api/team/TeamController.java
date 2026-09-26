package com.teamflow.api.team;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.team.dto.CreateTeamRequest;
import com.teamflow.core.team.dto.TeamMemberResponse;
import com.teamflow.core.team.dto.TeamResponse;
import com.teamflow.core.team.dto.UpdateTeamMemberRoleRequest;
import com.teamflow.core.team.service.TeamService;
import com.teamflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** 登录用户的团队与成员管理 REST 接口。 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = Objects.requireNonNull(
                teamService,
                "团队服务不能为 null"
        );
    }

    @Audited(
            action = "TEAM_CREATE",
            resourceType = "USER",
            resourceId = "#user.id"
    )
    @PostMapping
    public ApiResponse<TeamResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateTeamRequest request
    ) {
        return ApiResponse.success(teamService.create(user.id(), request));
    }

    @GetMapping
    public ApiResponse<List<TeamResponse>> listMine(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(teamService.listMine(user.id()));
    }

    @GetMapping("/{teamId}/members")
    public ApiResponse<List<TeamMemberResponse>> listMembers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String teamId
    ) {
        return ApiResponse.success(
                teamService.listMembers(user.id(), teamId)
        );
    }

    @Audited(
            action = "TEAM_MEMBER_ROLE_UPDATE",
            resourceType = "TEAM",
            resourceId = "#teamId"
    )
    @PatchMapping("/{teamId}/members/{memberUserId}/role")
    public ApiResponse<Void> updateMemberRole(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String teamId,
            @PathVariable String memberUserId,
            @Valid @RequestBody UpdateTeamMemberRoleRequest request
    ) {
        teamService.updateMemberRole(
                user.id(),
                teamId,
                memberUserId,
                request
        );
        return ApiResponse.success(null);
    }

    @Audited(
            action = "TEAM_MEMBER_REMOVE",
            resourceType = "TEAM",
            resourceId = "#teamId"
    )
    @DeleteMapping("/{teamId}/members/{memberUserId}")
    public ApiResponse<Void> removeMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String teamId,
            @PathVariable String memberUserId
    ) {
        teamService.removeMember(user.id(), teamId, memberUserId);
        return ApiResponse.success(null);
    }
}
