package com.teamflow.core.team.mapper;

import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.dto.TeamMemberResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 团队成员关系数据访问接口。
 */
@Mapper
public interface TeamMemberMapper {

    /**
     * 新增团队成员关系。
     *
     * @param member 待保存成员关系
     * @return 受影响行数
     */
    int insert(TeamMember member);

    /**
     * 查询指定团队成员关系。
     *
     * @param teamId 团队编号
     * @param userId 用户编号
     * @return 成员关系，可为空
     */
    Optional<TeamMember> findByTeamAndUser(
            @Param("teamId") String teamId,
            @Param("userId") String userId
    );

    /**
     * 按稳定顺序锁定本次成员管理涉及的成员关系。
     *
     * <p>该查询只提供并发协调，角色判断仍由 Java 权限服务完成。调用方必须
     * 位于事务中，并保证 {@code userIds} 非空。</p>
     *
     * @param teamId 团队编号
     * @param userIds 需要锁定的用户编号
     * @return 当前存在的成员关系
     */
    List<TeamMember> findByTeamAndUsersForUpdate(
            @Param("teamId") String teamId,
            @Param("userIds") List<String> userIds
    );

    /**
     * 查询团队全部成员。
     *
     * @param teamId 团队编号
     * @return 成员关系列表
     */
    List<TeamMember> findByTeamId(@Param("teamId") String teamId);

    /**
     * 查询团队成员的公开展示信息。
     *
     * <p>成员关系和用户公开资料在一条查询中组合，避免先查询成员关系后
     * 再逐个查询用户所产生的 N+1 问题。</p>
     *
     * @param teamId 团队编号
     * @return 团队成员公开信息列表
     */
    List<TeamMemberResponse> findDetailsByTeamId(
            @Param("teamId") String teamId
    );

    /**
     * 修改成员角色。
     *
     * @param teamId 团队编号
     * @param userId 用户编号
     * @param expectedRole 调用方读取成员关系时的角色
     * @param newRole 需要保存的新角色
     * @return 受影响行数
     */
    int updateRole(
            @Param("teamId") String teamId,
            @Param("userId") String userId,
            @Param("expectedRole") TeamRole expectedRole,
            @Param("newRole") TeamRole newRole
    );

    /**
     * 移除团队成员。
     *
     * <p>旧角色参与删除条件，防止权限校验完成后目标成员的角色已经被其他
     * 请求修改，当前请求却仍按过期角色删除成员。</p>
     *
     * @param teamId 团队编号
     * @param userId 用户编号
     * @param expectedRole 调用方读取成员关系时的角色
     * @return 受影响行数
     */
    int delete(
            @Param("teamId") String teamId,
            @Param("userId") String userId,
            @Param("expectedRole") TeamRole expectedRole
    );
}
