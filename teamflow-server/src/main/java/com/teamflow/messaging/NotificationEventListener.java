package com.teamflow.messaging;

import com.teamflow.core.notification.domain.NotificationType;
import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.dto.NotificationResponse;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.sse.SseConnectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 将任务指派事件转换成可持久查询的站内通知。 */
@Component
public class NotificationEventListener {

    public static final String NOTIFICATION_EVENT_NAME = "notification";

    private static final Logger log = LoggerFactory.getLogger(
            NotificationEventListener.class
    );

    private final NotificationService notificationService;
    private final SseConnectionRegistry sseRegistry;

    public NotificationEventListener(
            NotificationService notificationService,
            SseConnectionRegistry sseRegistry
    ) {
        this.notificationService = Objects.requireNonNull(
                notificationService,
                "通知服务不能为 null"
        );
        this.sseRegistry = Objects.requireNonNull(
                sseRegistry,
                "SSE 连接注册表不能为 null"
        );
    }

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    public void onAssignmentChanged(TaskAssignmentChangedEvent event) {
        Objects.requireNonNull(event, "任务负责人变化事件不能为 null");
        if (event.assigneeId() == null) {
            return;
        }

        NotificationResponse notification = Objects.requireNonNull(
                notificationService.create(new CreateNotificationCommand(
                        event.assigneeId(),
                        NotificationType.TASK_ASSIGNED,
                        "你被指派了一个任务",
                        "任务 " + event.taskId() + " 已指派给你",
                        "TASK_ASSIGNED:" + event.eventId()
                )),
                "通知服务不能返回 null"
        );

        try {
            sseRegistry.send(
                    event.assigneeId(),
                    NOTIFICATION_EVENT_NAME,
                    notification.id(),
                    notification
            );
        } catch (RuntimeException exception) {
            // 通知已经持久化，实时推送失败不能触发消息重复消费。
            log.warn(
                    "通知已入库但 SSE 推送失败 notificationId={}",
                    notification.id(),
                    exception
            );
        }
    }
}
