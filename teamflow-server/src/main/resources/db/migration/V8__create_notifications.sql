CREATE TABLE notifications (
    id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    is_read INTEGER NOT NULL,
    created_at VARCHAR(24) NOT NULL,
    read_at VARCHAR(24),

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_notifications_event_key UNIQUE (event_key)
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at, id);
CREATE INDEX idx_notifications_user_read_created
    ON notifications (user_id, is_read, created_at, id);
