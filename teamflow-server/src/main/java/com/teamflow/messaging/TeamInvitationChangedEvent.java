package com.teamflow.messaging;

import com.teamflow.core.team.domain.TeamInvitationStatus;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.event.TeamInvitationAction;

/** RabbitMQ 中传输的团队邀请变更快照。 */
public record TeamInvitationChangedEvent(
        String eventId,
        TeamInvitationAction action,
        String invitationId,
        String teamId,
        String teamName,
        TeamRole role,
        TeamInvitationStatus status,
        String inviterId,
        String inviteeId,
        String inviteeDisplayName,
        String recipientId,
        String operatorId,
        int invitationVersion,
        String occurredAt
) {
}
