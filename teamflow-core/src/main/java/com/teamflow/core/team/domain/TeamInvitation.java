package com.teamflow.core.team.domain;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.version.Versions;
import com.teamflow.core.team.error.TeamErrorCode;

import java.util.Objects;

/** 团队邀请聚合；它是邀请状态的唯一业务依据。 */
public class TeamInvitation {

    private final String id;
    private final String teamId;
    private final String inviterId;
    private final String inviteeId;
    private final TeamRole invitedRole;
    private final String createdAt;

    private TeamInvitationStatus status;
    private int version;
    private String respondedAt;
    private String revokedBy;
    private String updatedAt;

    public TeamInvitation(
            String id,
            String teamId,
            String inviterId,
            String inviteeId,
            TeamRole invitedRole,
            TeamInvitationStatus status,
            int version,
            String createdAt,
            String updatedAt,
            String respondedAt,
            String revokedBy
    ) {
        this.id = TextValues.requireNonBlank(id, "邀请编号");
        this.teamId = TextValues.requireNonBlank(teamId, "团队编号");
        this.inviterId = TextValues.requireNonBlank(inviterId, "邀请人编号");
        this.inviteeId = TextValues.requireNonBlank(inviteeId, "被邀请人编号");
        if (this.inviterId.equals(this.inviteeId)) {
            throw new IllegalArgumentException("邀请人和被邀请人不能相同");
        }
        this.invitedRole = Objects.requireNonNull(invitedRole, "邀请角色不能为 null");
        if (invitedRole == TeamRole.OWNER) {
            throw new IllegalArgumentException("普通邀请不能授予 OWNER");
        }
        this.status = Objects.requireNonNull(status, "邀请状态不能为 null");
        this.version = Versions.requireNonNegative(version, "邀请版本");
        this.createdAt = UtcTimeText.requireValid(createdAt, "创建时间");
        this.updatedAt = UtcTimeText.requireAtOrAfter(updatedAt, createdAt, "修改时间", "创建时间");
        this.respondedAt = respondedAt == null
                ? null
                : UtcTimeText.requireAtOrAfter(
                        respondedAt,
                        this.createdAt,
                        "处理时间",
                        "创建时间"
                );
        this.revokedBy = TextValues.requireOptionalNonBlank(revokedBy, "撤销人编号");
        requireStateFieldsConsistent();
    }

    public static TeamInvitation create(
            String id,
            String teamId,
            String inviterId,
            String inviteeId,
            TeamRole role,
            String now
    ) {
        return new TeamInvitation(id, teamId, inviterId, inviteeId, role,
                TeamInvitationStatus.PENDING, 0, now, now, null, null);
    }

    public void accept(String now) {
        transition(TeamInvitationStatus.ACCEPTED, now, null);
    }

    public void reject(String now) {
        transition(TeamInvitationStatus.REJECTED, now, null);
    }

    public void revoke(String operatorId, String now) {
        transition(TeamInvitationStatus.REVOKED, now,
                TextValues.requireNonBlank(operatorId, "撤销人编号"));
    }

    private void transition(TeamInvitationStatus target, String now, String revoker) {
        if (status != TeamInvitationStatus.PENDING) {
            throw new BusinessException(TeamErrorCode.INVITATION_NOT_PENDING);
        }
        String validTime = UtcTimeText.requireAtOrAfter(now, updatedAt,
                "处理时间", "当前修改时间");
        version = Versions.next(version, "邀请版本");
        status = target;
        respondedAt = validTime;
        revokedBy = revoker;
        updatedAt = validTime;
    }

    private void requireStateFieldsConsistent() {
        if (status == TeamInvitationStatus.PENDING
                && (respondedAt != null || revokedBy != null)) {
            throw new IllegalArgumentException("待处理邀请不能包含处理结果");
        }
        if (status != TeamInvitationStatus.PENDING && respondedAt == null) {
            throw new IllegalArgumentException("已处理邀请必须包含处理时间");
        }
        if (status == TeamInvitationStatus.REVOKED && revokedBy == null) {
            throw new IllegalArgumentException("已撤销邀请必须记录撤销人");
        }
        if (status != TeamInvitationStatus.REVOKED && revokedBy != null) {
            throw new IllegalArgumentException("非撤销状态不能记录撤销人");
        }
    }

    public String getId() { return id; }
    public String getTeamId() { return teamId; }
    public String getInviterId() { return inviterId; }
    public String getInviteeId() { return inviteeId; }
    public TeamRole getInvitedRole() { return invitedRole; }
    public TeamInvitationStatus getStatus() { return status; }
    public int getVersion() { return version; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public String getRespondedAt() { return respondedAt; }
    public String getRevokedBy() { return revokedBy; }
}
