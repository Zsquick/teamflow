package com.teamflow.core.notification.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;

import java.util.Objects;

/**
 * 站内通知持久化实体。
 */
public class Notification {

    private static final int MAX_ID_LENGTH = 32;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_EVENT_KEY_LENGTH = 128;

    private final String id;
    private final String userId;
    private final NotificationType type;
    private final String title;
    private final String content;
    private final String eventKey;
    private final boolean read;
    private final String createdAt;
    private final String readAt;

    /** 使用持久化字段还原一条通知。 */
    public Notification(
            String id,
            String userId,
            NotificationType type,
            String title,
            String content,
            String eventKey,
            boolean read,
            String createdAt,
            String readAt
    ) {
        this.id = requireSized(id, "通知编号", MAX_ID_LENGTH);
        this.userId = requireSized(
                userId,
                "收件用户编号",
                MAX_ID_LENGTH
        );
        this.type = Objects.requireNonNull(type, "通知类型不能为 null");
        this.title = requireSized(
                title,
                "通知标题",
                MAX_TITLE_LENGTH
        );
        this.content = requireSized(
                content,
                "通知内容",
                MAX_CONTENT_LENGTH
        );
        this.eventKey = requireSized(
                eventKey,
                "通知事件键",
                MAX_EVENT_KEY_LENGTH
        );
        this.read = read;
        this.createdAt = UtcTimeText.requireValid(
                createdAt,
                "通知创建时间"
        );
        this.readAt = requireConsistentReadAt(
                read,
                readAt,
                this.createdAt
        );
    }

    /** 创建一条初始为未读状态的新通知。 */
    public static Notification create(
            String id,
            String userId,
            NotificationType type,
            String title,
            String content,
            String eventKey,
            String now
    ) {
        return new Notification(
                id,
                userId,
                type,
                TextValues.requireNonBlank(title, "通知标题").strip(),
                TextValues.requireNonBlank(content, "通知内容").strip(),
                eventKey,
                false,
                now,
                null
        );
    }

    private static String requireConsistentReadAt(
            boolean read,
            String readAt,
            String createdAt
    ) {
        if (!read) {
            if (readAt != null) {
                throw new IllegalArgumentException(
                        "未读通知不能包含已读时间"
                );
            }
            return null;
        }
        if (readAt == null) {
            throw new IllegalArgumentException("已读通知必须包含已读时间");
        }
        return UtcTimeText.requireAtOrAfter(
                readAt,
                createdAt,
                "通知已读时间",
                "通知创建时间"
        );
    }

    private static String requireSized(
            String value,
            String fieldName,
            int maxLength
    ) {
        String valid = TextValues.requireNonBlank(value, fieldName);
        if (valid.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过 " + maxLength + " 个字符"
            );
        }
        return valid;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getEventKey() {
        return eventKey;
    }

    public boolean isRead() {
        return read;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getReadAt() {
        return readAt;
    }
}
