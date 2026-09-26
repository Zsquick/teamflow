CREATE TABLE id_sequences (
    sequence_name VARCHAR(32) NOT NULL,
    next_value INTEGER NOT NULL,

    CONSTRAINT pk_id_sequences PRIMARY KEY (sequence_name)
);

INSERT INTO id_sequences (sequence_name, next_value) VALUES ('USER', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('TEAM', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('TEAM_MEMBER', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('PROJECT', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('TASK', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('TASK_COMMENT', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('ATTACHMENT', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('IMPORT_JOB', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('NOTIFICATION', 1);
INSERT INTO id_sequences (sequence_name, next_value) VALUES ('OPERATION_LOG', 1);
