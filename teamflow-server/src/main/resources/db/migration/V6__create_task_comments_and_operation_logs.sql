CREATE TABLE task_comments (
    id VARCHAR(32) NOT NULL,
    task_id VARCHAR(32) NOT NULL,
    author_id VARCHAR(32) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at VARCHAR(24) NOT NULL,

    CONSTRAINT pk_task_comments PRIMARY KEY (id),
    CONSTRAINT fk_task_comments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_comments_author
        FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_task_comments_task_created
    ON task_comments (task_id, created_at, id);

CREATE TABLE operation_logs (
    id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(32),
    detail VARCHAR(500),
    success INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL,
    trace_id VARCHAR(64),
    ip_address VARCHAR(45),
    created_at VARCHAR(24) NOT NULL,

    CONSTRAINT pk_operation_logs PRIMARY KEY (id)
);

CREATE INDEX idx_operation_logs_resource_created
    ON operation_logs (resource_type, resource_id, created_at, id);
