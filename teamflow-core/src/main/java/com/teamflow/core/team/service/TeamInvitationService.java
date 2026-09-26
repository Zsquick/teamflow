package com.teamflow.core.team.service;

import com.teamflow.core.team.dto.CreateTeamInvitationRequest;
import com.teamflow.core.team.dto.InvitationPreviewRequest;
import com.teamflow.core.team.dto.InvitationUserPreviewResponse;
import com.teamflow.core.team.dto.TeamInvitationResponse;

import java.util.List;

/** 团队邀请完整业务流程。 */
public interface TeamInvitationService {
    InvitationUserPreviewResponse preview(
            String currentUserId,
            String teamId,
            InvitationPreviewRequest request
    );

    TeamInvitationResponse send(
            String currentUserId,
            String teamId,
            CreateTeamInvitationRequest request
    );

    List<TeamInvitationResponse> listReceived(String currentUserId);

    List<TeamInvitationResponse> listByTeam(
            String currentUserId,
            String teamId
    );

    void accept(String currentUserId, String invitationId);

    void reject(String currentUserId, String invitationId);

    void revoke(
            String currentUserId,
            String teamId,
            String invitationId
    );
}
