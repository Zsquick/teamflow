CREATE TABLE import_jobs (
    id VARCHAR(32) NOT NULL,
    project_id VARCHAR(32) NOT NULL,
    creator_id VARCHAR(32) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    job_status VARCHAR(16) NOT NULL,
    total_rows BIGINT NOT NULL,
    success_rows BIGINT NOT NULL,
    failed_rows BIGINT NOT NULL,
    error_file_path VARCHAR(512),
    created_at VARCHAR(24) NOT NULL,
    started_at VARCHAR(24),
    finished_at VARCHAR(24),

    CONSTRAINT pk_import_jobs PRIMARY KEY (id),
    CONSTRAINT fk_import_jobs_project
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_import_jobs_creator
        FOREIGN KEY (creator_id) REFERENCES users (id)
);

CREATE INDEX idx_import_jobs_project_created
    ON import_jobs (project_id, created_at, id);
CREATE INDEX idx_import_jobs_creator_created
    ON import_jobs (creator_id, created_at, id);
