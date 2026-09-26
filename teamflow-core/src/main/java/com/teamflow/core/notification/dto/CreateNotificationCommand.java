package com.teamflow.core.notification.dto;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.notification.domain.NotificationType;

import java.util.Objects;

/**
 * 内部创建通知命令。
 *
 * @param userId 收件用户
 * @param type 通知类型
 * @param title 标题
 * @param content 内容
 * @param eventKey 来源事件的幂等键
 */
public record CreateNotificationCommand(
        String userId,
        NotificationType type,
        String title,
        String content,
        String eventKey
) {

    /** 校验内部事件字段，并规范化面向用户展示的文本。 */
    public CreateNotificationCommand {
        userId = TextValues.requireNonBlank(userId, "收件用户编号");
        type = Objects.requireNonNull(type, "通知类型不能为 null");
        title = TextValues.requireNonBlank(title, "通知标题").strip();
        content = TextValues.requireNonBlank(
                content,
                "通知内容"
        ).strip();
        eventKey = TextValues.requireNonBlank(
                eventKey,
                "通知事件键"
        ).strip();
    }
}
