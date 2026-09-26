package com.teamflow.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.outbox.domain.OutboxEvent;
import com.teamflow.core.outbox.mapper.OutboxEventMapper;
import com.teamflow.core.team.event.TeamInvitationChanged;
import com.teamflow.core.team.service.TeamInvitationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 在邀请业务事务中写入可重试的 Outbox 事件。 */
@Service
public class OutboxTeamInvitationEventPublisher
        implements TeamInvitationEventPublisher {

    private final OutboxEventMapper mapper;
    private final ReadableIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public OutboxTeamInvitationEventPublisher(
            OutboxEventMapper mapper,
            ReadableIdGenerator idGenerator,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(TeamInvitationChanged event) {
        Objects.requireNonNull(event, "邀请事件不能为 null");
        TeamInvitationChangedEvent transport = new TeamInvitationChangedEvent(
                event.eventKey(), event.action(), event.invitationId(),
                event.teamId(), event.teamName(), event.role(), event.status(),
                event.inviterId(), event.inviteeId(), event.inviteeDisplayName(),
                event.recipientId(), event.operatorId(), event.invitationVersion(),
                event.occurredAt()
        );
        OutboxEvent outbox = OutboxEvent.pendingPayload(
                idGenerator.nextId(ResourceType.OUTBOX_EVENT),
                event.eventKey(),
                OutboxEvent.TEAM_INVITATION_CHANGED,
                event.invitationId(),
                event.teamId(),
                event.invitationVersion(),
                event.recipientId(),
                event.operatorId(),
                event.occurredAt(),
                writePayload(transport)
        );
        if (mapper.insert(outbox) != 1) {
            throw new IllegalStateException("新增邀请 Outbox 事件失败");
        }
    }

    private String writePayload(TeamInvitationChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化邀请事件失败", exception);
        }
    }
}
