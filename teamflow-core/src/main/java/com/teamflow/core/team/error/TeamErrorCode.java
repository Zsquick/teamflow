package com.teamflow.core.team.error;

import com.teamflow.common.error.ErrorCode;

/**
 * 团队、成员和资源级 RBAC 使用的稳定业务错误码。
 */
public enum TeamErrorCode implements ErrorCode {
    TEAM_NOT_FOUND("TEAM_0001", "团队不存在或无权访问", 404),
    USER_NOT_FOUND("TEAM_0002", "目标用户不存在", 404),
    MEMBER_ALREADY_EXISTS("TEAM_0003", "用户已是团队成员", 409),
    MEMBER_NOT_FOUND("TEAM_0004", "团队成员不存在", 404),
    INSUFFICIENT_PERMISSION("TEAM_0005", "当前团队角色无权执行该操作", 403),
    SELF_MANAGEMENT_NOT_ALLOWED("TEAM_0006", "不能通过成员管理接口操作自己", 409),
    OWNER_ROLE_PROTECTED("TEAM_0007", "所有者角色不能通过成员管理接口变更", 409),
    ROLE_NOT_ASSIGNABLE("TEAM_0008", "当前角色无权授予目标角色", 403),
    MEMBER_CHANGE_CONFLICT("TEAM_0009", "成员关系已发生变化，请刷新后重试", 409),
    INVITATION_ALREADY_PENDING("TEAM_0010", "该用户已有待处理的团队邀请", 409),
    INVITATION_NOT_FOUND("TEAM_0011", "团队邀请不存在或无权访问", 404),
    INVITATION_NOT_PENDING("TEAM_0012", "团队邀请已处理，请刷新后重试", 409),
    INVITATION_CHANGE_CONFLICT("TEAM_0013", "团队邀请已发生变化，请刷新后重试", 409),
    INVITATION_TARGET_INVALID("TEAM_0014", "该用户当前不能接受团队邀请", 409),
    SELF_INVITATION_NOT_ALLOWED("TEAM_0015", "不能邀请自己加入团队", 409),
    INVITATION_NO_LONGER_VALID("TEAM_0016", "邀请已不再满足加入条件，请联系团队管理员", 409);

    private final String code;
    private final String message;
    private final int httpStatus;

    TeamErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
