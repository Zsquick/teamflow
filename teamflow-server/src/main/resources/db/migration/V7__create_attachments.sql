CREATE TABLE attachments (
    id VARCHAR(32) NOT NULL,
    task_id VARCHAR(32) NOT NULL,
    uploader_id VARCHAR(32) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_name VARCHAR(64) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at VARCHAR(24) NOT NULL,

    CONSTRAINT pk_attachments PRIMARY KEY (id),
    CONSTRAINT fk_attachments_task
        FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_attachments_uploader
        FOREIGN KEY (uploader_id) REFERENCES users (id),
    CONSTRAINT uk_attachments_storage_path UNIQUE (storage_path)
);

CREATE INDEX idx_attachments_task_created
    ON attachments (task_id, created_at, id);
