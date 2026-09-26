package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.TeamInvitationStatus;
import com.teamflow.core.team.domain.TeamRole;

/** 邀请列表查询的内部投影，不直接返回给前端。 */
public record TeamInvitationDetails(
        String id,
        String teamId,
        String teamName,
        String inviterId,
        String inviterDisplayName,
        String inviteeId,
        String inviteeUsername,
        String inviteeEmail,
        String inviteeDisplayName,
        TeamRole role,
        TeamInvitationStatus status,
        int version,
        String createdAt,
        String updatedAt,
        String respondedAt
) {
}
