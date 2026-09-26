package com.teamflow.core.audit.mapper;

import com.teamflow.core.audit.domain.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志数据访问接口。
 */
@Mapper
public interface OperationLogMapper {

    /**
     * 新增操作日志。
     *
     * @param operationLog 待保存日志
     * @return 受影响行数
     */
    int insert(OperationLog operationLog);

    /**
     * 查询指定资源的操作历史。
     *
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param limit 最大返回数量
     * @param offset 跳过的记录数
     * @return 按创建时间和编号稳定倒序排列的操作日志列表
     */
    List<OperationLog> findByResource(
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
