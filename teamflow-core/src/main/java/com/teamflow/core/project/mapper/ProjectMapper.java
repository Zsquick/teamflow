package com.teamflow.core.project.mapper;

import com.teamflow.core.project.domain.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 项目数据访问接口。
 */
@Mapper
public interface ProjectMapper {

    /**
     * 新增项目。
     *
     * @param project 待保存项目
     * @return 受影响行数
     */
    int insert(Project project);

    /**
     * 按标识查询项目。
     *
     * @param id 项目标识
     * @return 项目，可为空
     */
    Optional<Project> findById(@Param("id") String id);

    /**
     * 在当前事务内锁定项目行，供随后执行的跨资源写操作使用。
     *
     * @param id 项目标识
     * @return 项目，可为空
     */
    Optional<Project> findByIdForUpdate(@Param("id") String id);

    /**
     * 统计团队内规范化项目短标识的占用数量。
     *
     * @param teamId 团队编号
     * @param projectKey 已规范化为大写的项目短标识
     * @return 匹配数量
     */
    int countByTeamIdAndProjectKey(
            @Param("teamId") String teamId,
            @Param("projectKey") String projectKey
    );

    /**
     * 查询团队项目。
     *
     * @param teamId 团队标识
     * @return 项目列表
     */
    List<Project> findByTeamId(@Param("teamId") String teamId);

    /**
     * 根据乐观锁版本修改项目。
     *
     * @param project 待修改项目
     * @param expectedVersion 读取项目时观察到的版本
     * @return 受影响行数
     */
    int update(
            @Param("project") Project project,
            @Param("expectedVersion") int expectedVersion
    );
}
