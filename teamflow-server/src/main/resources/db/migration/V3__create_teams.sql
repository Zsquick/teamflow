CREATE TABLE teams (
    id VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(500),
    owner_id VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL,
    created_at VARCHAR(24) NOT NULL,
    updated_at VARCHAR(24) NOT NULL,

    CONSTRAINT pk_teams PRIMARY KEY (id),
    CONSTRAINT fk_teams_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE team_members (
    id VARCHAR(32) NOT NULL,
    team_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    member_role VARCHAR(16) NOT NULL,
    joined_at VARCHAR(24) NOT NULL,

    CONSTRAINT pk_team_members PRIMARY KEY (id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_team_members_team_user UNIQUE (team_id, user_id)
);

CREATE INDEX idx_teams_owner_id ON teams (owner_id);
CREATE INDEX idx_team_members_user_team ON team_members (user_id, team_id);
