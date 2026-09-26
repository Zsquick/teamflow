package com.teamflow.core.team.service;

import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;

/**
 * 团队资源级 RBAC 契约，集中管理角色层级和成员边界。
 *
 * <p>调用锁定式管理校验后还要执行写操作的业务方法，本身必须开启事务，
 * 使成员行锁持续到对应的 insert、update 或 delete 完成。</p>
 */
public interface TeamAuthorizationService {

    /** 要求用户是团队成员，否则抛出业务异常。 */
    TeamMember requireMember(String teamId, String userId);

    /**
     * 要求用户至少具有给定角色等级，并锁定操作者成员关系供后续写操作使用。
     */
    TeamRole requireAtLeast(String teamId, String userId, TeamRole minimumRole);

    /**
     * 按稳定顺序锁定操作者和可选关联成员，并要求二者都属于团队。
     *
     * <p>关联成员可为空，也可与操作者相同。操作者缺失时返回不可见团队，
     * 关联成员缺失时返回成员不存在，供上层映射成具体资源错误。</p>
     */
    void requireMembersForUpdate(
            String teamId,
            String operatorId,
            String relatedUserId
    );

    /**
     * 锁定操作者和目标成员，校验自我管理、OWNER 保护及严格角色层级，
     * 并返回同一次查询得到的角色上下文。
     */
    TeamManagementContext requireCanManageMember(
            String teamId,
            String operatorId,
            String targetUserId
    );
}
