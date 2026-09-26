package com.teamflow.core.common.id;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 编号序列的数据访问接口。
 */
@Mapper
public interface IdSequenceMapper {

    /**
     * 查询下一个可分配序号，并在当前事务结束前锁定该序列行。
     *
     * @param sequenceName 序列名称
     * @return 下一个可分配序号；不存在时返回 {@code null}
     */
    Integer findNextValueForUpdate(@Param("sequenceName") String sequenceName);

    /**
     * 保存下一次调用应该使用的序号。
     *
     * @param sequenceName 序列名称
     * @param nextValue 下一次可分配序号
     * @return 受影响行数
     */
    int updateNextValue(
            @Param("sequenceName") String sequenceName,
            @Param("nextValue") int nextValue
    );
}
