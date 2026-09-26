CREATE TABLE outbox_events (
    id VARCHAR(32) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    task_id VARCHAR(32) NOT NULL,
    project_id VARCHAR(32) NOT NULL,
    task_version INTEGER NOT NULL,
    previous_assignee_id VARCHAR(32),
    assignee_id VARCHAR(32),
    operator_id VARCHAR(32) NOT NULL,
    occurred_at VARCHAR(24) NOT NULL,
    event_status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at VARCHAR(24) NOT NULL,
    locked_by VARCHAR(64),
    locked_at VARCHAR(24),
    published_at VARCHAR(24),
    last_error VARCHAR(1000),
    created_at VARCHAR(24) NOT NULL,
    updated_at VARCHAR(24) NOT NULL,

    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_event_key UNIQUE (event_key)
);

CREATE INDEX idx_outbox_events_ready
    ON outbox_events (event_status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_events_stale
    ON outbox_events (event_status, locked_at);

INSERT INTO id_sequences (sequence_name, next_value)
VALUES ('OUTBOX_EVENT', 1);
