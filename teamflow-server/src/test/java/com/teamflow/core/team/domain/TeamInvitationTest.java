package com.teamflow.core.team.domain;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.team.error.TeamErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeamInvitationTest {

    private static final String CREATED = "2026-09-21T01:00:00.000Z";
    private static final String CHANGED = "2026-09-21T01:01:00.000Z";

    @Test
    void shouldAcceptPendingInvitationOnce() {
        TeamInvitation invitation = invitation();

        invitation.accept(CHANGED);

        assertEquals(TeamInvitationStatus.ACCEPTED, invitation.getStatus());
        assertEquals(1, invitation.getVersion());
        assertEquals(CHANGED, invitation.getRespondedAt());
        assertNull(invitation.getRevokedBy());
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> invitation.reject(CHANGED)
        );
        assertEquals(TeamErrorCode.INVITATION_NOT_PENDING,
                exception.getErrorCode());
    }

    @Test
    void shouldRecordRevoker() {
        TeamInvitation invitation = invitation();

        invitation.revoke("u003", CHANGED);

        assertEquals(TeamInvitationStatus.REVOKED, invitation.getStatus());
        assertEquals("u003", invitation.getRevokedBy());
    }

    private static TeamInvitation invitation() {
        return TeamInvitation.create(
                "ti001", "tm001", "u001", "u002",
                TeamRole.MEMBER, CREATED
        );
    }
}
