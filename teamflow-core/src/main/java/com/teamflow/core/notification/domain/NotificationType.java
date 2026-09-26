package com.teamflow.core.notification.domain;

/**
 * 站内通知类型。
 */
public enum NotificationType {
    TASK_ASSIGNED,
    TASK_DUE_SOON,
    COMMENT_ADDED,
    IMPORT_COMPLETED,
    IMPORT_FAILED,
    TEAM_INVITATION_RECEIVED,
    TEAM_INVITATION_ACCEPTED,
    TEAM_INVITATION_REJECTED,
    TEAM_INVITATION_REVOKED
}
