package com.teamflow.core.team.mapper;

import com.teamflow.core.team.domain.Team;
import com.teamflow.core.team.dto.TeamResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 团队数据访问接口。
 */
@Mapper
public interface TeamMapper {

    /**
     * 新增团队。
     *
     * @param team 待保存团队
     * @return 受影响行数
     */
    int insert(Team team);

    /**
     * 按标识查询团队。
     *
     * @param id 团队编号
     * @return 团队，可为空
     */
    Optional<Team> findById(@Param("id") String id);

    /** 在事务中锁定团队，串行化同一团队的邀请状态变更。 */
    Optional<Team> findByIdForUpdate(@Param("id") String id);

    /**
     * 查询用户加入的团队。
     *
     * <p>查询会同时返回当前用户在每个团队中的角色，供业务层和前端决定
     * 可以展示的操作入口。该查询一次完成团队和成员关系的组合，避免逐团队
     * 查询角色。</p>
     *
     * @param userId 用户编号
     * @return 带当前用户角色的团队响应列表
     */
    List<TeamResponse> findByUserId(@Param("userId") String userId);

    /**
     * 根据调用方读取数据时的版本号修改团队。
     *
     * @param team 待修改团队
     * @param expectedVersion 调用方读取数据时的版本号
     * @return 受影响行数
     */
    int update(
            @Param("team") Team team,
            @Param("expectedVersion") int expectedVersion
    );
}
