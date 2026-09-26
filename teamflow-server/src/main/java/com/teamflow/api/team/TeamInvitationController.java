package com.teamflow.api.team;

import com.teamflow.audit.Audited;
import com.teamflow.common.api.ApiResponse;
import com.teamflow.core.team.dto.CreateTeamInvitationRequest;
import com.teamflow.core.team.dto.InvitationPreviewRequest;
import com.teamflow.core.team.dto.InvitationUserPreviewResponse;
import com.teamflow.core.team.dto.TeamInvitationResponse;
import com.teamflow.core.team.service.TeamInvitationService;
import com.teamflow.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** 团队邀请的查找、发送、回应和撤销接口。 */
@RestController
@RequestMapping("/api")
public class TeamInvitationController {

    private final TeamInvitationService service;

    public TeamInvitationController(TeamInvitationService service) {
        this.service = Objects.requireNonNull(service);
    }

    @PostMapping("/teams/{teamId}/invitations/preview")
    public ApiResponse<InvitationUserPreviewResponse> preview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String teamId,
            @Valid @RequestBody InvitationPreviewRequest request
    ) {
        return ApiResponse.success(service.preview(user.id(), teamId, request));
    }

    @Audited(action = "TEAM_INVITATION_SEND", resourceType = "TEAM",
            resourceId = "#teamId")
    @PostMapping("/teams/{teamId}/invitations")
    public ApiResponse<TeamInvitationResponse> send(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String teamId,
            @Valid @RequestBody CreateTeamInvitationRequest request
    ) {
        return ApiResponse.success(service.send(user.id(), teamId, request));
    }

    @GetMapping("/teams/{teamId}/invitations")
    public ApiResponse<List<TeamInvitationResponse>> listByTeam(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String teamId
    ) {
        return ApiResponse.success(service.listByTeam(user.id(), teamId));
    }

    @Audited(action = "TEAM_INVITATION_REVOKE", resourceType = "TEAM_INVITATION",
            resourceId = "#invitationId")
    @PostMapping("/teams/{teamId}/invitations/{invitationId}/revoke")
    public ApiResponse<Void> revoke(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String teamId,
            @PathVariable String invitationId
    ) {
        service.revoke(user.id(), teamId, invitationId);
        return ApiResponse.success(null);
    }

    @GetMapping("/invitations")
    public ApiResponse<List<TeamInvitationResponse>> listReceived(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(service.listReceived(user.id()));
    }

    @Audited(action = "TEAM_INVITATION_ACCEPT", resourceType = "TEAM_INVITATION",
            resourceId = "#invitationId")
    @PostMapping("/invitations/{invitationId}/accept")
    public ApiResponse<Void> accept(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String invitationId
    ) {
        service.accept(user.id(), invitationId);
        return ApiResponse.success(null);
    }

    @Audited(action = "TEAM_INVITATION_REJECT", resourceType = "TEAM_INVITATION",
            resourceId = "#invitationId")
    @PostMapping("/invitations/{invitationId}/reject")
    public ApiResponse<Void> reject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String invitationId
    ) {
        service.reject(user.id(), invitationId);
        return ApiResponse.success(null);
    }
}
