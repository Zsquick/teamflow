package com.teamflow.core.importjob.mapper;

import com.teamflow.core.importjob.domain.ImportJob;
import com.teamflow.core.importjob.domain.ImportJobStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Optional;

/**
 * CSV 导入任务数据访问接口。
 */
@Mapper
public interface ImportJobMapper {

    /**
     * 新增导入任务。
     *
     * @param importJob 待保存导入任务
     * @return 受影响行数
     */
    int insert(ImportJob importJob);

    /**
     * 按标识查询导入任务。
     *
     * @param id 导入任务标识
     * @return 导入任务，可为空
     */
    Optional<ImportJob> findById(@Param("id") String id);

    /**
     * 修改导入任务状态和统计数据。
     *
     * @param importJob 待修改导入任务
     * @param expectedStatus 修改前必须匹配的状态
     * @return 受影响行数
     */
    int update(
            @Param("importJob") ImportJob importJob,
            @Param("expectedStatus") ImportJobStatus expectedStatus
    );
}
