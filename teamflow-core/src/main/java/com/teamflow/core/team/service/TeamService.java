package com.teamflow.core.team.service;

import com.teamflow.core.team.dto.CreateTeamRequest;
import com.teamflow.core.team.dto.TeamMemberResponse;
import com.teamflow.core.team.dto.TeamResponse;
import com.teamflow.core.team.dto.UpdateTeamMemberRoleRequest;
import java.util.List;

/**
 * 团队业务契约。
 */
public interface TeamService {

    /**
     * 创建团队并将创建者设为所有者。
     *
     * @param currentUserId 当前用户标识
     * @param request 创建请求
     * @return 新团队
     */
    TeamResponse create(String currentUserId, CreateTeamRequest request);

    /**
     * 查询当前用户加入的团队。
     *
     * @param currentUserId 当前用户标识
     * @return 团队列表
     */
    List<TeamResponse> listMine(String currentUserId);

    /**
     * 查询团队成员。
     *
     * @param currentUserId 当前用户标识
     * @param teamId 团队标识
     * @return 成员列表
     */
    List<TeamMemberResponse> listMembers(String currentUserId, String teamId);

    /**
     * 修改团队成员角色。
     *
     * @param currentUserId 当前用户标识
     * @param teamId 团队标识
     * @param memberUserId 成员用户标识
     * @param request 新角色
     */
    void updateMemberRole(
            String currentUserId,
            String teamId,
            String memberUserId,
            UpdateTeamMemberRoleRequest request
    );

    /**
     * 移除团队成员。
     *
     * @param currentUserId 当前用户标识
     * @param teamId 团队标识
     * @param memberUserId 成员用户标识
     */
    void removeMember(String currentUserId, String teamId, String memberUserId);
}
