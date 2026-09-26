package com.teamflow.core.team.error;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 团队与资源级 RBAC 错误码协议测试。 */
class TeamErrorCodeTest {

    @Test
    void shouldExposeStableResourceAndMembershipMappings() {
        assertMapping(
                TeamErrorCode.TEAM_NOT_FOUND,
                "TEAM_0001",
                "团队不存在或无权访问",
                404
        );
        assertMapping(
                TeamErrorCode.USER_NOT_FOUND,
                "TEAM_0002",
                "待添加用户不存在",
                404
        );
        assertMapping(
                TeamErrorCode.MEMBER_ALREADY_EXISTS,
                "TEAM_0003",
                "用户已是团队成员",
                409
        );
        assertMapping(
                TeamErrorCode.MEMBER_NOT_FOUND,
                "TEAM_0004",
                "团队成员不存在",
                404
        );
    }

    @Test
    void shouldExposeStableAuthorizationAndConflictMappings() {
        assertMapping(
                TeamErrorCode.INSUFFICIENT_PERMISSION,
                "TEAM_0005",
                "当前团队角色无权执行该操作",
                403
        );
        assertMapping(
                TeamErrorCode.SELF_MANAGEMENT_NOT_ALLOWED,
                "TEAM_0006",
                "不能通过成员管理接口操作自己",
                409
        );
        assertMapping(
                TeamErrorCode.OWNER_ROLE_PROTECTED,
                "TEAM_0007",
                "所有者角色不能通过成员管理接口变更",
                409
        );
        assertMapping(
                TeamErrorCode.ROLE_NOT_ASSIGNABLE,
                "TEAM_0008",
                "当前角色无权授予目标角色",
                403
        );
        assertMapping(
                TeamErrorCode.MEMBER_CHANGE_CONFLICT,
                "TEAM_0009",
                "成员关系已发生变化，请刷新后重试",
                409
        );
    }

    @Test
    void shouldKeepEveryTeamBusinessCodeUnique() {
        Set<String> codes = Arrays.stream(TeamErrorCode.values())
                .map(TeamErrorCode::code)
                .collect(Collectors.toSet());

        assertEquals(TeamErrorCode.values().length, codes.size());
    }

    private void assertMapping(
            TeamErrorCode errorCode,
            String code,
            String message,
            int httpStatus
    ) {
        assertEquals(code, errorCode.code());
        assertEquals(message, errorCode.message());
        assertEquals(httpStatus, errorCode.httpStatus());
    }
}
