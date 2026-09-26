package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.TeamRole;

import java.util.List;

/** 发送邀请前展示的脱敏用户摘要。 */
public record InvitationUserPreviewResponse(
        String displayName,
        String maskedUsername,
        String maskedEmail,
        List<TeamRole> assignableRoles
) {
    public InvitationUserPreviewResponse {
        assignableRoles = List.copyOf(assignableRoles);
    }
}
