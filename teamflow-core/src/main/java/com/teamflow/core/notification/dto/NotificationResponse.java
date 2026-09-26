package com.teamflow.core.notification.dto;

import com.teamflow.core.notification.domain.Notification;
import com.teamflow.core.notification.domain.NotificationType;

import java.util.Objects;

/**
 * 站内通知响应。
 *
 * @param id 通知标识
 * @param type 通知类型
 * @param title 标题
 * @param content 内容
 * @param read 是否已读
 * @param createdAt 创建时间
 */
public record NotificationResponse(
        String id,
        NotificationType type,
        String title,
        String content,
        boolean read,
        String createdAt
) {

    /** 将通知领域对象转换为不含内部幂等键的接口响应。 */
    public static NotificationResponse from(Notification notification) {
        Notification valid = Objects.requireNonNull(
                notification,
                "通知不能为 null"
        );
        return new NotificationResponse(
                valid.getId(),
                valid.getType(),
                valid.getTitle(),
                valid.getContent(),
                valid.isRead(),
                valid.getCreatedAt()
        );
    }
}
