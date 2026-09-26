package com.teamflow.core.team.event;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;
import com.teamflow.core.team.domain.TeamInvitationStatus;
import com.teamflow.core.team.domain.TeamRole;

import java.util.Objects;

/** 与邀请业务修改一起写入 Outbox 的不可变事件快照。 */
public record TeamInvitationChanged(
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
    public TeamInvitationChanged {
        action = Objects.requireNonNull(action, "邀请动作不能为 null");
        invitationId = TextValues.requireNonBlank(invitationId, "邀请编号");
        teamId = TextValues.requireNonBlank(teamId, "团队编号");
        teamName = TextValues.requireNonBlank(teamName, "团队名称");
        role = Objects.requireNonNull(role, "邀请角色不能为 null");
        status = Objects.requireNonNull(status, "邀请状态不能为 null");
        inviterId = TextValues.requireNonBlank(inviterId, "邀请人编号");
        inviteeId = TextValues.requireNonBlank(inviteeId, "被邀请人编号");
        inviteeDisplayName = TextValues.requireNonBlank(inviteeDisplayName, "被邀请人名称");
        recipientId = TextValues.requireNonBlank(recipientId, "通知收件人");
        operatorId = TextValues.requireNonBlank(operatorId, "操作人编号");
        invitationVersion = Versions.requireNonNegative(invitationVersion, "邀请版本");
        occurredAt = UtcTimeText.requireValid(occurredAt, "事件时间");
    }

    public String eventKey() {
        return invitationId + ":" + invitationVersion + ":"
                + action + ":" + recipientId;
    }
}
