package com.teamflow.integration.commentaudit;

import com.teamflow.core.audit.domain.OperationLog;
import com.teamflow.core.audit.mapper.OperationLogMapper;
import com.teamflow.core.comment.domain.TaskComment;
import com.teamflow.core.comment.dto.CommentResponse;
import com.teamflow.core.comment.mapper.TaskCommentMapper;
import org.apache.ibatis.annotations.Mapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 8.4 验证 V6、评论联表映射和追加式审计日志。
 */
@SpringBootTest(
        classes = CommentAuditPersistenceIntegrationTest.PersistenceTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.datasource.hikari.maximum-pool-size=2",
                "spring.datasource.hikari.minimum-idle=0",
                "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
                "mybatis.configuration.map-underscore-to-camel-case=true",
                "spring.batch.job.enabled=false"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CommentAuditPersistenceIntegrationTest {

    private static final String EARLIER_AT =
            "2026-09-18T01:02:03.456Z";
    private static final String LATER_AT =
            "2026-09-18T02:03:04.567Z";
    private static final String PROJECT_ID = "p001";
    private static final String TASK_ID = "t001";
    private static final String FIRST_USER_ID = "u001";
    private static final String SECOND_USER_ID = "u002";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(com.teamflow.integration.TestContainerImages.MYSQL)
                    .withDatabaseName("teamflow_comment_audit_test")
                    .withUsername("teamflow_comment_audit_test")
                    .withPassword("teamflow_comment_audit_test_password");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskCommentMapper commentMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @DynamicPropertySource
    static void registerDataSourceProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                MYSQL::getDriverClassName
        );
    }

    @Test
    void shouldApplyV6AndJoinAuthorsInStableCommentOrder() {
        assertTrue(
                Arrays.stream(flyway.info().applied())
                        .map(MigrationInfo::getVersion)
                        .anyMatch(MigrationVersion.fromVersion("6")::equals)
        );
        insertTaskFixture();

        assertEquals(
                1,
                commentMapper.insert(comment(
                        "c002",
                        FIRST_USER_ID,
                        "Second at the same time",
                        LATER_AT
                ))
        );
        assertEquals(
                1,
                commentMapper.insert(comment(
                        "c003",
                        SECOND_USER_ID,
                        "Chronologically first",
                        EARLIER_AT
                ))
        );
        assertEquals(
                1,
                commentMapper.insert(comment(
                        "c001",
                        FIRST_USER_ID,
                        "First by identifier at the same time",
                        LATER_AT
                ))
        );

        List<CommentResponse> comments =
                commentMapper.findDetailsByTaskId(TASK_ID);
        CommentResponse detail = commentMapper.findDetailById("c003")
                .orElseThrow();
        TaskComment stored = commentMapper.findById("c001")
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        List.of("c003", "c001", "c002"),
                        comments.stream().map(CommentResponse::id).toList()
                ),
                () -> assertEquals(
                        List.of(
                                "Second Author",
                                "First Author",
                                "First Author"
                        ),
                        comments.stream()
                                .map(CommentResponse::authorName)
                                .toList()
                ),
                () -> assertEquals("c003", detail.id()),
                () -> assertEquals(SECOND_USER_ID, detail.authorId()),
                () -> assertEquals("Second Author", detail.authorName()),
                () -> assertEquals("Chronologically first", detail.content()),
                () -> assertEquals(EARLIER_AT, detail.createdAt()),
                () -> assertEquals(TASK_ID, stored.getTaskId()),
                () -> assertEquals(FIRST_USER_ID, stored.getAuthorId()),
                () -> assertEquals(
                        "First by identifier at the same time",
                        stored.getContent()
                )
        );
    }

    @Test
    void shouldDeleteCommentOnlyWhenAuthorMatches() {
        insertTaskFixture();
        assertEquals(
                1,
                commentMapper.insert(comment(
                        "c001",
                        FIRST_USER_ID,
                        "Protected by the author predicate",
                        EARLIER_AT
                ))
        );

        assertEquals(
                0,
                commentMapper.deleteByIdAndAuthor(
                        "c001",
                        SECOND_USER_ID
                )
        );
        assertTrue(commentMapper.findById("c001").isPresent());

        assertEquals(
                1,
                commentMapper.deleteByIdAndAuthor(
                        "c001",
                        FIRST_USER_ID
                )
        );
        assertTrue(commentMapper.findById("c001").isEmpty());
        assertEquals(
                0,
                commentMapper.deleteByIdAndAuthor(
                        "c001",
                        FIRST_USER_ID
                )
        );
    }

    @Test
    void shouldEnforceCommentTaskForeignKey() {
        insertTaskFixture();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> commentMapper.insert(new TaskComment(
                        "c001",
                        "t404",
                        FIRST_USER_ID,
                        "Missing task",
                        EARLIER_AT
                ))
        );
    }

    @Test
    void shouldEnforceCommentAuthorForeignKey() {
        insertTaskFixture();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> commentMapper.insert(new TaskComment(
                        "c001",
                        TASK_ID,
                        "u404",
                        "Missing author",
                        EARLIER_AT
                ))
        );
    }

    @Test
    void shouldRoundTripAuditFieldsAndPageByNewestFirst() {
        assertEquals(
                1,
                operationLogMapper.insert(operationLog(
                        "op001",
                        null,
                        false,
                        0,
                        null,
                        null,
                        null,
                        EARLIER_AT
                ))
        );
        assertEquals(
                1,
                operationLogMapper.insert(operationLog(
                        "op002",
                        FIRST_USER_ID,
                        true,
                        12,
                        "Updated a task",
                        "trace-002",
                        "127.0.0.1",
                        LATER_AT
                ))
        );
        assertEquals(
                1,
                operationLogMapper.insert(operationLog(
                        "op004",
                        SECOND_USER_ID,
                        false,
                        34,
                        "Business conflict",
                        "trace-004",
                        "2001:db8:0:0:0:0:0:1",
                        LATER_AT
                ))
        );
        assertEquals(
                1,
                operationLogMapper.insert(operationLog(
                        "op003",
                        FIRST_USER_ID,
                        true,
                        23,
                        "Moved a task",
                        "trace-003",
                        "127.0.0.1",
                        LATER_AT
                ))
        );
        assertEquals(
                1,
                operationLogMapper.insert(new OperationLog(
                        "op005",
                        FIRST_USER_ID,
                        "COMMENT_CREATE",
                        "TASK",
                        null,
                        null,
                        true,
                        1,
                        null,
                        null,
                        LATER_AT
                ))
        );

        List<OperationLog> all = operationLogMapper.findByResource(
                "TASK",
                TASK_ID,
                10,
                0
        );
        List<OperationLog> middlePage = operationLogMapper.findByResource(
                "TASK",
                TASK_ID,
                2,
                1
        );
        OperationLog oldest = all.get(3);

        assertAll(
                () -> assertEquals(
                        List.of("op004", "op003", "op002", "op001"),
                        all.stream().map(OperationLog::getId).toList()
                ),
                () -> assertEquals(
                        List.of("op003", "op002"),
                        middlePage.stream()
                                .map(OperationLog::getId)
                                .toList()
                ),
                () -> assertEquals("TASK_UPDATE", oldest.getAction()),
                () -> assertEquals("TASK", oldest.getResourceType()),
                () -> assertEquals(TASK_ID, oldest.getResourceId()),
                () -> assertFalse(oldest.isSuccess()),
                () -> assertEquals(0L, oldest.getDurationMs()),
                () -> assertNull(oldest.getUserId()),
                () -> assertNull(oldest.getDetail()),
                () -> assertNull(oldest.getTraceId()),
                () -> assertNull(oldest.getIpAddress()),
                () -> assertEquals(EARLIER_AT, oldest.getCreatedAt()),
                () -> assertEquals(
                        1L,
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM operation_logs "
                                        + "WHERE resource_id IS NULL",
                                Long.class
                        )
                )
        );
    }

    private TaskComment comment(
            String id,
            String authorId,
            String content,
            String createdAt
    ) {
        return new TaskComment(
                id,
                TASK_ID,
                authorId,
                content,
                createdAt
        );
    }

    private OperationLog operationLog(
            String id,
            String userId,
            boolean success,
            long durationMs,
            String detail,
            String traceId,
            String ipAddress,
            String createdAt
    ) {
        return new OperationLog(
                id,
                userId,
                "TASK_UPDATE",
                "TASK",
                TASK_ID,
                detail,
                success,
                durationMs,
                traceId,
                ipAddress,
                createdAt
        );
    }

    private void insertTaskFixture() {
        insertUser(FIRST_USER_ID, "first_user", "First Author");
        insertUser(SECOND_USER_ID, "second_user", "Second Author");
        assertEquals(
                1,
                jdbcTemplate.update(
                        """
                        INSERT INTO teams (
                            id,
                            name,
                            description,
                            owner_id,
                            version,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                        "tm001",
                        "Comment Integration Team",
                        null,
                        FIRST_USER_ID,
                        0,
                        EARLIER_AT,
                        EARLIER_AT
                )
        );
        assertEquals(
                1,
                jdbcTemplate.update(
                        """
                        INSERT INTO projects (
                            id,
                            team_id,
                            name,
                            project_key,
                            description,
                            project_status,
                            created_by,
                            version,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        PROJECT_ID,
                        "tm001",
                        "Comment Integration Project",
                        "COMMENT",
                        null,
                        "ACTIVE",
                        FIRST_USER_ID,
                        0,
                        EARLIER_AT,
                        EARLIER_AT
                )
        );
        assertEquals(
                1,
                jdbcTemplate.update(
                        """
                        INSERT INTO tasks (
                            id,
                            project_id,
                            title,
                            description,
                            task_status,
                            priority,
                            assignee_id,
                            reporter_id,
                            due_at,
                            version,
                            created_at,
                            updated_at,
                            deleted_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        TASK_ID,
                        PROJECT_ID,
                        "Comment Integration Task",
                        null,
                        "TODO",
                        "MEDIUM",
                        null,
                        FIRST_USER_ID,
                        null,
                        0,
                        EARLIER_AT,
                        EARLIER_AT,
                        null
                )
        );
    }

    private void insertUser(
            String id,
            String username,
            String displayName
    ) {
        assertEquals(
                1,
                jdbcTemplate.update(
                        """
                        INSERT INTO users (
                            id,
                            username,
                            email,
                            password_hash,
                            display_name,
                            avatar_url,
                            status,
                            version,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        id,
                        username,
                        username + "@example.com",
                        "test-password-hash",
                        displayName,
                        null,
                        "ACTIVE",
                        0,
                        EARLIER_AT,
                        EARLIER_AT
                )
        );
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            MybatisAutoConfiguration.class
    })
    @MapperScan(
            basePackageClasses = {
                    TaskCommentMapper.class,
                    OperationLogMapper.class
            },
            annotationClass = Mapper.class
    )
    static class PersistenceTestApplication {
    }
}
