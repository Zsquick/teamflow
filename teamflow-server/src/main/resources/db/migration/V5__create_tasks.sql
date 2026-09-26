CREATE TABLE tasks (
    id VARCHAR(32) NOT NULL,
    project_id VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(5000),
    task_status VARCHAR(16) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    assignee_id VARCHAR(32),
    reporter_id VARCHAR(32) NOT NULL,
    due_at VARCHAR(24),
    version INTEGER NOT NULL,
    created_at VARCHAR(24) NOT NULL,
    updated_at VARCHAR(24) NOT NULL,
    deleted_at VARCHAR(24),

    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_project
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_tasks_assignee
        FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_reporter
        FOREIGN KEY (reporter_id) REFERENCES users (id)
);

CREATE INDEX idx_tasks_project_created
    ON tasks (project_id, deleted_at, created_at, id);
CREATE INDEX idx_tasks_project_status_created
    ON tasks (project_id, deleted_at, task_status, created_at, id);
CREATE INDEX idx_tasks_project_assignee
    ON tasks (project_id, deleted_at, assignee_id);
CREATE INDEX idx_tasks_due
    ON tasks (deleted_at, task_status, due_at, id);
