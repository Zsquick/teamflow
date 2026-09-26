CREATE TABLE projects (
    id VARCHAR(32) NOT NULL,
    team_id VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    project_key VARCHAR(16) NOT NULL,
    description VARCHAR(1000),
    project_status VARCHAR(16) NOT NULL,
    created_by VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL,
    created_at VARCHAR(24) NOT NULL,
    updated_at VARCHAR(24) NOT NULL,

    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_projects_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT uk_projects_team_key UNIQUE (team_id, project_key)
);

CREATE INDEX idx_projects_team_created ON projects (team_id, created_at, id);
