package com.teamflow.core.comment.domain;

import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;

/**
 * 不可编辑的任务评论实体。
 */
public class TaskComment {

    private static final int MAX_CONTENT_LENGTH = 2000;

    private final String id;
    private final String taskId;
    private final String authorId;
    private final String content;
    private final String createdAt;

    /** 使用持久化数据还原评论。 */
    public TaskComment(
            String id,
            String taskId,
            String authorId,
            String content,
            String createdAt
    ) {
        this.id = TextValues.requireNonBlank(id, "评论编号");
        this.taskId = TextValues.requireNonBlank(taskId, "任务编号");
        this.authorId = TextValues.requireNonBlank(authorId, "作者编号");
        this.content = requireValidContent(content);
        this.createdAt = UtcTimeText.requireValid(
                createdAt,
                "评论创建时间"
        );
    }

    /** 创建首尾空白已清理的新评论。 */
    public static TaskComment create(
            String id,
            String taskId,
            String authorId,
            String content,
            String now
    ) {
        String normalizedContent = TextValues.requireNonBlank(
                content,
                "评论内容"
        ).strip();
        return new TaskComment(
                id,
                taskId,
                authorId,
                normalizedContent,
                now
        );
    }

    private static String requireValidContent(String content) {
        String validContent = TextValues.requireNonBlank(
                content,
                "评论内容"
        );
        if (validContent.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "评论内容不能超过 "
                            + MAX_CONTENT_LENGTH
                            + " 个字符"
            );
        }
        return validContent;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
