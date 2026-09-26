package com.teamflow.core.team.service;

import com.teamflow.core.team.event.TeamInvitationChanged;

/** 将邀请事件与当前业务事务原子写入 Outbox。 */
public interface TeamInvitationEventPublisher {
    void publish(TeamInvitationChanged event);
}
