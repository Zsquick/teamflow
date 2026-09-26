package com.teamflow.core.comment.service;

import com.teamflow.core.comment.dto.CommentResponse;
import com.teamflow.core.comment.dto.CreateCommentRequest;
import java.util.List;

/**
 * 任务评论业务契约。
 */
public interface TaskCommentService {

    /**
     * 新增任务评论。
     *
     * @param currentUserId 当前用户标识
     * @param taskId 任务标识
     * @param request 评论内容
     * @return 新评论
     */
    CommentResponse create(
            String currentUserId,
            String taskId,
            CreateCommentRequest request
    );

    /**
     * 查询任务评论。
     *
     * @param currentUserId 当前用户标识
     * @param taskId 任务标识
     * @return 评论列表
     */
    List<CommentResponse> listByTask(String currentUserId, String taskId);

    /**
     * 删除本人评论。
     *
     * @param currentUserId 当前用户标识
     * @param commentId 评论标识
     */
    void delete(String currentUserId, String commentId);
}
