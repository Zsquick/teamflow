package com.teamflow.messaging;

import com.teamflow.core.notification.domain.NotificationType;
import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.dto.NotificationResponse;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.core.team.event.TeamInvitationAction;
import com.teamflow.sse.SseConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 把邀请事件持久化为站内通知，再尝试通过 SSE 实时提醒。 */
@Component
public class InvitationNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(
            InvitationNotificationEventListener.class);

    private final NotificationService notificationService;
    private final SseConnectionRegistry sseRegistry;

    public InvitationNotificationEventListener(
            NotificationService notificationService,
            SseConnectionRegistry sseRegistry
    ) {
        this.notificationService = Objects.requireNonNull(notificationService);
        this.sseRegistry = Objects.requireNonNull(sseRegistry);
    }

    @RabbitListener(queues = RabbitMqConfig.INVITATION_QUEUE)
    public void onInvitationChanged(TeamInvitationChangedEvent event) {
        Objects.requireNonNull(event, "邀请变更事件不能为 null");
        NotificationText text = text(event);
        NotificationResponse notification = notificationService.create(
                new CreateNotificationCommand(
                        event.recipientId(),
                        text.type(),
                        text.title(),
                        text.content(),
                        "TEAM_INVITATION:" + event.eventId()
                )
        );
        try {
            sseRegistry.send(
                    event.recipientId(),
                    NotificationEventListener.NOTIFICATION_EVENT_NAME,
                    notification.id(),
                    notification
            );
        } catch (RuntimeException exception) {
            log.warn("邀请通知已入库但 SSE 推送失败 notificationId={}",
                    notification.id(), exception);
        }
    }

    private static NotificationText text(TeamInvitationChangedEvent event) {
        return switch (event.action()) {
            case CREATED -> new NotificationText(
                    NotificationType.TEAM_INVITATION_RECEIVED,
                    "收到团队邀请",
                    "你被邀请以" + roleName(event) + "身份加入团队“"
                            + event.teamName() + "”"
            );
            case ACCEPTED -> new NotificationText(
                    NotificationType.TEAM_INVITATION_ACCEPTED,
                    "团队邀请已接受",
                    event.inviteeDisplayName() + " 已加入团队“"
                            + event.teamName() + "”"
            );
            case REJECTED -> new NotificationText(
                    NotificationType.TEAM_INVITATION_REJECTED,
                    "团队邀请已拒绝",
                    event.inviteeDisplayName() + " 拒绝了团队“"
                            + event.teamName() + "”的邀请"
            );
            case REVOKED -> new NotificationText(
                    NotificationType.TEAM_INVITATION_REVOKED,
                    "团队邀请已撤销",
                    revokedContent(event)
            );
        };
    }

    private static String roleName(TeamInvitationChangedEvent event) {
        return switch (event.role()) {
            case ADMIN -> "管理员";
            case MEMBER -> "成员";
            case OWNER -> "所有者";
        };
    }

    private static String revokedContent(TeamInvitationChangedEvent event) {
        if (event.recipientId().equals(event.inviteeId())) {
            return "你收到的团队“" + event.teamName() + "”邀请已撤销";
        }
        return "团队“" + event.teamName() + "”中发送给 "
                + event.inviteeDisplayName() + " 的邀请已撤销";
    }

    private record NotificationText(
            NotificationType type,
            String title,
            String content
    ) {
    }
}
