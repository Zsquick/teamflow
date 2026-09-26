package com.teamflow.core.file.mapper;

import com.teamflow.core.file.domain.Attachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 附件元数据访问接口。
 */
@Mapper
public interface AttachmentMapper {

    /**
     * 新增附件元数据。
     *
     * @param attachment 附件元数据
     * @return 受影响行数
     */
    int insert(Attachment attachment);

    /**
     * 按标识查询附件。
     *
     * @param id 附件标识
     * @return 附件，可为空
     */
    Optional<Attachment> findById(@Param("id") String id);

    /**
     * 按标识查询并锁定附件元数据。
     *
     * @param id 附件标识
     * @return 附件，可为空
     */
    Optional<Attachment> findByIdForUpdate(@Param("id") String id);

    /**
     * 查询任务附件。
     *
     * @param taskId 任务标识
     * @return 附件列表
     */
    List<Attachment> findByTaskId(@Param("taskId") String taskId);

    /**
     * 删除附件元数据。
     *
     * @param id 附件标识
     * @return 受影响行数
     */
    int deleteById(@Param("id") String id);
}
