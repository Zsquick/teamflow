package com.teamflow.core.comment.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.comment.domain.TaskComment;
import com.teamflow.core.comment.dto.CommentResponse;
import com.teamflow.core.comment.dto.CreateCommentRequest;
import com.teamflow.core.comment.error.CommentErrorCode;
import com.teamflow.core.comment.mapper.TaskCommentMapper;
import com.teamflow.core.comment.service.TaskCommentService;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.service.TaskAccessService;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务评论创建、查询和作者删除的默认实现。
 */
@Service
public class TaskCommentServiceImpl implements TaskCommentService {
    private final TaskCommentMapper commentMapper;
    private final TaskAccessService taskAccessService;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;

    /**
     * 创建评论业务实现。
     *
     * @param commentMapper 评论数据访问接口
     * @param taskAccessService 任务访问边界
     * @param idGenerator 可读资源编号生成器
     * @param clock 可测试时钟
     */
    public TaskCommentServiceImpl(
            TaskCommentMapper commentMapper,
            TaskAccessService taskAccessService,
            ReadableIdGenerator idGenerator,
            Clock clock
    ) {
        this.commentMapper = Objects.requireNonNull(
                commentMapper,
                "评论 Mapper 不能为 null"
        );
        this.taskAccessService = Objects.requireNonNull(
                taskAccessService,
                "任务访问服务不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "评论时钟不能为 null");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public CommentResponse create(
            String currentUserId,
            String taskId,
            CreateCommentRequest request
    ) {
        Objects.requireNonNull(request, "新增评论请求不能为 null");
        taskAccessService.requireTaskMemberForUpdate(
                currentUserId,
                taskId
        );

        TaskComment comment = TaskComment.create(
                idGenerator.nextId(ResourceType.TASK_COMMENT),
                taskId,
                currentUserId,
                request.content(),
                UtcTimeText.now(clock)
        );
        requireSingleInsert(commentMapper.insert(comment));
        return commentMapper.findDetailById(comment.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "评论新增成功后无法重新读取: "
                                + comment.getId()
                ));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> listByTask(
            String currentUserId,
            String taskId
    ) {
        taskAccessService.requireTaskMember(currentUserId, taskId);
        return List.copyOf(
                commentMapper.findDetailsByTaskId(taskId)
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(String currentUserId, String commentId) {
        TaskComment comment = commentMapper.findById(commentId)
                .orElseThrow(() -> new BusinessException(
                        CommentErrorCode.COMMENT_NOT_FOUND
                ));
        requireCommentTaskForUpdate(
                currentUserId,
                comment.getTaskId()
        );
        if (!comment.getAuthorId().equals(currentUserId)) {
            throw new BusinessException(
                    CommentErrorCode.COMMENT_DELETE_FORBIDDEN
            );
        }

        int affectedRows = commentMapper.deleteByIdAndAuthor(
                commentId,
                currentUserId
        );
        if (affectedRows == 1) {
            return;
        }
        if (affectedRows != 0) {
            throw new IllegalStateException(
                    "删除评论时受影响行数只能为 0 或 1"
            );
        }
        throw new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    private void requireCommentTaskForUpdate(
            String currentUserId,
            String taskId
    ) {
        try {
            taskAccessService.requireTaskMemberForUpdate(
                    currentUserId,
                    taskId
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != TaskErrorCode.TASK_NOT_FOUND) {
                throw exception;
            }
            throw new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private static void requireSingleInsert(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增评论时受影响行数必须为 1"
            );
        }
    }
}
