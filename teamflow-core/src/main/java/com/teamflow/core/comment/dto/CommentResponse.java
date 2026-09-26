package com.teamflow.core.comment.dto;

/**
 * 任务评论响应。
 *
 * @param id 评论标识
 * @param taskId 任务标识
 * @param authorId 作者标识
 * @param authorName 作者展示名称
 * @param content 评论正文
 * @param createdAt 创建时间
 */
public record CommentResponse(
        String id,
        String taskId,
        String authorId,
        String authorName,
        String content,
        String createdAt
) {
}
