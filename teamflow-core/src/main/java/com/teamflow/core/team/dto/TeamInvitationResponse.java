package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.TeamInvitationStatus;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.user.support.UserIdentityMasker;

/** 脱敏后的团队邀请响应。 */
public record TeamInvitationResponse(
        String id,
        String teamId,
        String teamName,
        String inviterId,
        String inviterDisplayName,
        String inviteeId,
        String inviteeDisplayName,
        String maskedInviteeUsername,
        String maskedInviteeEmail,
        TeamRole role,
        TeamInvitationStatus status,
        int version,
        String createdAt,
        String updatedAt,
        String respondedAt
) {
    public static TeamInvitationResponse from(TeamInvitationDetails details) {
        return new TeamInvitationResponse(
                details.id(), details.teamId(), details.teamName(),
                details.inviterId(), details.inviterDisplayName(),
                details.inviteeId(), details.inviteeDisplayName(),
                UserIdentityMasker.maskUsername(details.inviteeUsername()),
                UserIdentityMasker.maskEmail(details.inviteeEmail()),
                details.role(), details.status(), details.version(),
                details.createdAt(), details.updatedAt(), details.respondedAt()
        );
    }
}
