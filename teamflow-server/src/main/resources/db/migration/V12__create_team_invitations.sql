CREATE TABLE team_invitations (
    id VARCHAR(32) NOT NULL,
    team_id VARCHAR(32) NOT NULL,
    inviter_id VARCHAR(32) NOT NULL,
    invitee_id VARCHAR(32) NOT NULL,
    invited_role VARCHAR(16) NOT NULL,
    invitation_status VARCHAR(16) NOT NULL,
    version INTEGER NOT NULL,
    created_at VARCHAR(24) NOT NULL,
    updated_at VARCHAR(24) NOT NULL,
    responded_at VARCHAR(24),
    revoked_by VARCHAR(32),

    CONSTRAINT pk_team_invitations PRIMARY KEY (id),
    CONSTRAINT fk_team_invitations_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_team_invitations_inviter FOREIGN KEY (inviter_id) REFERENCES users (id),
    CONSTRAINT fk_team_invitations_invitee FOREIGN KEY (invitee_id) REFERENCES users (id),
    CONSTRAINT fk_team_invitations_revoker FOREIGN KEY (revoked_by) REFERENCES users (id)
);

CREATE INDEX idx_team_invitations_invitee
    ON team_invitations (invitee_id, invitation_status, created_at);
CREATE INDEX idx_team_invitations_team
    ON team_invitations (team_id, invitation_status, created_at);

ALTER TABLE outbox_events ADD COLUMN event_payload VARCHAR(4000);

INSERT INTO id_sequences (sequence_name, next_value)
VALUES ('TEAM_INVITATION', 1);
