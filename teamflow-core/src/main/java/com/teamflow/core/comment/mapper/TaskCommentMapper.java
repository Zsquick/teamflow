package com.teamflow.core.comment.mapper;

import com.teamflow.core.comment.domain.TaskComment;
import com.teamflow.core.comment.dto.CommentResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Optional;

/**
 * 任务评论数据访问接口。
 */
@Mapper
public interface TaskCommentMapper {

    /**
     * 新增评论。
     *
     * @param comment 待保存评论
     * @return 受影响行数
     */
    int insert(TaskComment comment);

    /**
     * 查询任务评论。
     *
     * @param taskId 任务标识
     * @return 评论列表
     */
    List<CommentResponse> findDetailsByTaskId(
            @Param("taskId") String taskId
    );

    /**
     * 按评论编号查询包含作者展示名的详情。
     *
     * @param id 评论编号
     * @return 评论公开详情，可为空
     */
    Optional<CommentResponse> findDetailById(@Param("id") String id);

    /**
     * 按标识查询评论。
     *
     * @param id 评论标识
     * @return 评论，可为空
     */
    Optional<TaskComment> findById(@Param("id") String id);

    /**
     * 删除属于指定作者的评论。
     *
     * @param id 评论标识
     * @param authorId 作者标识
     * @return 受影响行数
     */
    int deleteByIdAndAuthor(
            @Param("id") String id,
            @Param("authorId") String authorId
    );
}
