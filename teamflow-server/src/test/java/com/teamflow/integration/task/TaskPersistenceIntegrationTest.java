package com.teamflow.integration.task;

import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.domain.TaskPriority;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.mapper.AssigneeCompletedCount;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.task.mapper.TaskStatusCount;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 8.4 验证 V5、任务 MyBatis 映射、查询和乐观锁语义。
 */
@SpringBootTest(
        classes = TaskPersistenceIntegrationTest.PersistenceTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.datasource.hikari.maximum-pool-size=4",
                "spring.datasource.hikari.minimum-idle=0",
                "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
                "mybatis.configuration.map-underscore-to-camel-case=true",
                "spring.batch.job.enabled=false",
                "teamflow.jwt.secret-base64="
                        + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        }
)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class TaskPersistenceIntegrationTest {

    private static final String CREATED_AT = "2026-09-16T01:02:03.456Z";
    private static final String UPDATED_AT = "2026-09-16T02:03:04.567Z";
    private static final String PROJECT_ID = "p001";
    private static final String REPORTER_ID = "u001";
    private static final String FIRST_ASSIGNEE_ID = "u002";
    private static final String SECOND_ASSIGNEE_ID = "u003";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(com.teamflow.integration.TestContainerImages.MYSQL)
                    .withDatabaseName("teamflow_task_test")
                    .withUsername("teamflow_task_test")
                    .withPassword("teamflow_task_test_password");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                MYSQL::getDriverClassName
        );
    }

    @Test
    void shouldApplyV5AndRoundTripStringsEnumsUtcTextsAndNullableFields() {
        assertTrue(
                Arrays.stream(flyway.info().applied())
                        .map(MigrationInfo::getVersion)
                        .anyMatch(MigrationVersion.fromVersion("5")::equals)
        );
        insertBaseFixture();

        Task complete = new Task(
                "t001",
                PROJECT_ID,
                "Mapped task",
                "All optional fields are present",
                TaskStatus.IN_PROGRESS,
                TaskPriority.URGENT,
                FIRST_ASSIGNEE_ID,
                REPORTER_ID,
                "2026-09-20T08:30:00.000Z",
                7,
                CREATED_AT,
                UPDATED_AT
        );
        Task nullable = Task.create(
                "t002",
                PROJECT_ID,
                "Nullable task",
                null,
                TaskPriority.LOW,
                null,
                REPORTER_ID,
                null,
                CREATED_AT
        );

        assertEquals(1, taskMapper.insert(complete));
        assertEquals(1, taskMapper.insert(nullable));

        Task reloadedComplete = taskMapper.findById("t001").orElseThrow();
        Task reloadedNullable = taskMapper.findById("t002").orElseThrow();
        assertAll(
                () -> assertEquals("t001", reloadedComplete.getId()),
                () -> assertEquals(PROJECT_ID, reloadedComplete.getProjectId()),
                () -> assertEquals("Mapped task", reloadedComplete.getTitle()),
                () -> assertEquals(
                        "All optional fields are present",
                        reloadedComplete.getDescription()
                ),
                () -> assertEquals(
                        TaskStatus.IN_PROGRESS,
                        reloadedComplete.getStatus()
                ),
                () -> assertEquals(TaskPriority.URGENT, reloadedComplete.getPriority()),
                () -> assertEquals(
                        FIRST_ASSIGNEE_ID,
                        reloadedComplete.getAssigneeId()
                ),
                () -> assertEquals(REPORTER_ID, reloadedComplete.getReporterId()),
                () -> assertEquals(
                        "2026-09-20T08:30:00.000Z",
                        reloadedComplete.getDueAt()
                ),
                () -> assertEquals(7, reloadedComplete.getVersion()),
                () -> assertEquals(CREATED_AT, reloadedComplete.getCreatedAt()),
                () -> assertEquals(UPDATED_AT, reloadedComplete.getUpdatedAt()),
                () -> assertEquals("t002", reloadedNullable.getId()),
                () -> assertEquals(TaskStatus.TODO, reloadedNullable.getStatus()),
                () -> assertEquals(TaskPriority.LOW, reloadedNullable.getPriority()),
                () -> assertEquals(null, reloadedNullable.getDescription()),
                () -> assertEquals(null, reloadedNullable.getAssigneeId()),
                () -> assertEquals(null, reloadedNullable.getDueAt()),
                () -> assertEquals(0, reloadedNullable.getVersion()),
                () -> assertEquals(CREATED_AT, reloadedNullable.getCreatedAt()),
                () -> assertEquals(CREATED_AT, reloadedNullable.getUpdatedAt())
        );
    }

    @Test
    void shouldFilterPageAndCountTasksInStableCreatedAtAndIdOrder() {
        insertBaseFixture();
        List<Task> tasks = List.of(
                task("t001", "First TODO", TaskStatus.TODO, CREATED_AT),
                task(
                        "t002",
                        "In progress",
                        TaskStatus.IN_PROGRESS,
                        "2026-09-16T01:02:04.000Z"
                ),
                task(
                        "t003",
                        "Second TODO",
                        TaskStatus.TODO,
                        "2026-09-16T01:02:05.000Z"
                ),
                task(
                        "t004",
                        "Third TODO",
                        TaskStatus.TODO,
                        "2026-09-16T01:02:05.000Z"
                ),
                task(
                        "t006",
                        "Fourth TODO",
                        TaskStatus.TODO,
                        "2026-09-16T01:02:05.000Z"
                ),
                task(
                        "t005",
                        "Done",
                        TaskStatus.DONE,
                        "2026-09-16T01:02:06.000Z"
                )
        );

        assertEquals(tasks.size(), taskMapper.batchInsert(tasks));

        List<Task> todoPage = taskMapper.findByProjectId(
                PROJECT_ID,
                TaskStatus.TODO,
                2,
                1
        );
        List<Task> unfilteredPage = taskMapper.findByProjectId(
                PROJECT_ID,
                null,
                3,
                2
        );

        assertAll(
                () -> assertEquals(
                        List.of("t003", "t004"),
                        todoPage.stream().map(Task::getId).toList()
                ),
                () -> assertEquals(
                        List.of("t003", "t004", "t006"),
                        unfilteredPage.stream().map(Task::getId).toList()
                ),
                () -> assertEquals(
                        4L,
                        taskMapper.countByProjectId(PROJECT_ID, TaskStatus.TODO)
                ),
                () -> assertEquals(
                        1L,
                        taskMapper.countByProjectId(PROJECT_ID, TaskStatus.DONE)
                ),
                () -> assertEquals(
                        6L,
                        taskMapper.countByProjectId(PROJECT_ID, null)
                )
        );
    }

    @Test
    void shouldUpdateOnlyWhenExpectedVersionMatches() {
        insertBaseFixture();
        assertEquals(
                1,
                taskMapper.insert(task("t001", "Original", TaskStatus.TODO, CREATED_AT))
        );
        Task candidate = taskMapper.findById("t001").orElseThrow();
        int expectedVersion = candidate.getVersion();

        assertTrue(candidate.updateDetails(
                "Updated",
                "Updated description",
                TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH,
                FIRST_ASSIGNEE_ID,
                "2026-09-21T00:00:00.000Z",
                UPDATED_AT
        ));
        assertEquals(0, taskMapper.update(candidate, expectedVersion + 1));

        Task unchanged = taskMapper.findById("t001").orElseThrow();
        assertAll(
                () -> assertEquals("Original", unchanged.getTitle()),
                () -> assertEquals(TaskStatus.TODO, unchanged.getStatus()),
                () -> assertEquals(0, unchanged.getVersion()),
                () -> assertEquals(CREATED_AT, unchanged.getUpdatedAt())
        );

        assertEquals(1, taskMapper.update(candidate, expectedVersion));
        Task updated = taskMapper.findById("t001").orElseThrow();
        assertAll(
                () -> assertEquals("Updated", updated.getTitle()),
                () -> assertEquals("Updated description", updated.getDescription()),
                () -> assertEquals(TaskStatus.IN_PROGRESS, updated.getStatus()),
                () -> assertEquals(TaskPriority.HIGH, updated.getPriority()),
                () -> assertEquals(FIRST_ASSIGNEE_ID, updated.getAssigneeId()),
                () -> assertEquals("2026-09-21T00:00:00.000Z", updated.getDueAt()),
                () -> assertEquals(1, updated.getVersion()),
                () -> assertEquals(UPDATED_AT, updated.getUpdatedAt())
        );
    }

    @Test
    void shouldSoftDeleteByVersionAndHideThePhysicalRowFromEveryReadPath() {
        insertBaseFixture();
        Task task = new Task(
                "t001",
                PROJECT_ID,
                "Task to delete",
                null,
                TaskStatus.DONE,
                TaskPriority.MEDIUM,
                FIRST_ASSIGNEE_ID,
                REPORTER_ID,
                "2026-09-16T01:30:00.000Z",
                0,
                CREATED_AT,
                CREATED_AT
        );
        assertEquals(1, taskMapper.insert(task));

        String deletedAt = "2026-09-16T03:04:05.678Z";
        assertEquals(0, taskMapper.softDelete("t001", 1, deletedAt));
        assertTrue(taskMapper.findById("t001").isPresent());
        assertEquals(1, taskMapper.softDelete("t001", 0, deletedAt));

        assertAll(
                () -> assertTrue(taskMapper.findById("t001").isEmpty()),
                () -> assertTrue(
                        taskMapper.findByProjectId(PROJECT_ID, null, 10, 0).isEmpty()
                ),
                () -> assertEquals(0L, taskMapper.countByProjectId(PROJECT_ID, null)),
                () -> assertTrue(
                        taskMapper.findDueBetween(
                                "2026-09-16T00:00:00.000Z",
                                "2026-09-17T00:00:00.000Z",
                                10,
                                0
                        ).isEmpty()
                ),
                () -> assertTrue(taskMapper.countByStatus(PROJECT_ID).isEmpty()),
                () -> assertEquals(
                        0L,
                        taskMapper.countOverdue(
                                PROJECT_ID,
                                "2026-09-17T00:00:00.000Z"
                        )
                ),
                () -> assertTrue(
                        taskMapper.countCompletedByAssignee(PROJECT_ID).isEmpty()
                ),
                () -> assertEquals(0, taskMapper.softDelete("t001", 0, deletedAt))
        );

        assertAll(
                () -> assertEquals(
                        1L,
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM tasks WHERE id = ?",
                                Long.class,
                                "t001"
                        )
                ),
                () -> assertEquals(
                        1,
                        jdbcTemplate.queryForObject(
                                "SELECT version FROM tasks WHERE id = ?",
                                Integer.class,
                                "t001"
                        )
                ),
                () -> assertEquals(
                        deletedAt,
                        jdbcTemplate.queryForObject(
                                "SELECT deleted_at FROM tasks WHERE id = ?",
                                String.class,
                                "t001"
                        )
                ),
                () -> assertEquals(
                        deletedAt,
                        jdbcTemplate.queryForObject(
                                "SELECT updated_at FROM tasks WHERE id = ?",
                                String.class,
                                "t001"
                        )
                )
        );
    }

    @Test
    void shouldUseHalfOpenDueIntervalAndExcludeDoneAndSoftDeletedTasks() {
        insertBaseFixture();
        String from = "2026-09-20T10:00:00.000Z";
        String to = "2026-09-20T12:00:00.000Z";
        List<Task> tasks = List.of(
                taskWithDue("t001", TaskStatus.TODO, from),
                taskWithDue(
                        "t002",
                        TaskStatus.IN_PROGRESS,
                        "2026-09-20T11:00:00.000Z"
                ),
                taskWithDue("t003", TaskStatus.TODO, to),
                taskWithDue(
                        "t004",
                        TaskStatus.DONE,
                        "2026-09-20T11:30:00.000Z"
                ),
                taskWithDue("t005", TaskStatus.TODO, null),
                taskWithDue(
                        "t006",
                        TaskStatus.TODO,
                        "2026-09-20T10:30:00.000Z"
                )
        );
        assertEquals(tasks.size(), taskMapper.batchInsert(tasks));
        assertEquals(
                1,
                taskMapper.softDelete(
                        "t006",
                        0,
                        "2026-09-20T09:00:00.000Z"
                )
        );

        List<Task> due = taskMapper.findDueBetween(from, to, 10, 0);
        List<Task> secondPage = taskMapper.findDueBetween(from, to, 1, 1);

        assertAll(
                () -> assertEquals(
                        List.of("t001", "t002"),
                        due.stream().map(Task::getId).toList()
                ),
                () -> assertEquals(
                        List.of("t002"),
                        secondPage.stream().map(Task::getId).toList()
                )
        );
    }

    @Test
    void shouldAggregateVisibleStatusOverdueAndCompletedAssigneeCounts() {
        insertBaseFixture();
        String now = "2026-09-20T12:00:00.000Z";
        List<Task> tasks = List.of(
                statisticalTask(
                        "t001", TaskStatus.TODO, FIRST_ASSIGNEE_ID,
                        "2026-09-20T11:00:00.000Z"
                ),
                statisticalTask(
                        "t002", TaskStatus.TODO, FIRST_ASSIGNEE_ID,
                        "2026-09-20T13:00:00.000Z"
                ),
                statisticalTask(
                        "t003", TaskStatus.IN_PROGRESS, SECOND_ASSIGNEE_ID,
                        "2026-09-20T10:00:00.000Z"
                ),
                statisticalTask(
                        "t004", TaskStatus.DONE, FIRST_ASSIGNEE_ID,
                        "2026-09-20T09:00:00.000Z"
                ),
                statisticalTask("t005", TaskStatus.DONE, FIRST_ASSIGNEE_ID, null),
                statisticalTask("t006", TaskStatus.DONE, SECOND_ASSIGNEE_ID, null),
                statisticalTask("t007", TaskStatus.DONE, null, null),
                statisticalTask("t008", TaskStatus.DONE, FIRST_ASSIGNEE_ID, null),
                statisticalTask(
                        "t009", TaskStatus.TODO, SECOND_ASSIGNEE_ID,
                        "2026-09-20T08:00:00.000Z"
                )
        );
        assertEquals(tasks.size(), taskMapper.batchInsert(tasks));
        assertEquals(
                1,
                taskMapper.softDelete("t008", 0, "2026-09-20T11:30:00.000Z")
        );
        assertEquals(
                1,
                taskMapper.softDelete("t009", 0, "2026-09-20T11:31:00.000Z")
        );

        assertAll(
                () -> assertEquals(
                        List.of(
                                new TaskStatusCount(TaskStatus.DONE, 4),
                                new TaskStatusCount(TaskStatus.IN_PROGRESS, 1),
                                new TaskStatusCount(TaskStatus.TODO, 2)
                        ),
                        taskMapper.countByStatus(PROJECT_ID)
                ),
                () -> assertEquals(2L, taskMapper.countOverdue(PROJECT_ID, now)),
                () -> assertEquals(
                        List.of(
                                new AssigneeCompletedCount(FIRST_ASSIGNEE_ID, 2),
                                new AssigneeCompletedCount(SECOND_ASSIGNEE_ID, 1)
                        ),
                        taskMapper.countCompletedByAssignee(PROJECT_ID)
                )
        );
    }

    @Test
    void shouldEnforceProjectAssigneeAndReporterForeignKeys() {
        insertBaseFixture();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> taskMapper.insert(new Task(
                        "t901", "p999", "Unknown project", null,
                        TaskStatus.TODO, TaskPriority.MEDIUM, null, REPORTER_ID,
                        null, 0, CREATED_AT, CREATED_AT
                ))
        );
        assertThrows(
                DataIntegrityViolationException.class,
                () -> taskMapper.insert(new Task(
                        "t902", PROJECT_ID, "Unknown assignee", null,
                        TaskStatus.TODO, TaskPriority.MEDIUM, "u999", REPORTER_ID,
                        null, 0, CREATED_AT, CREATED_AT
                ))
        );
        assertThrows(
                DataIntegrityViolationException.class,
                () -> taskMapper.insert(new Task(
                        "t903", PROJECT_ID, "Unknown reporter", null,
                        TaskStatus.TODO, TaskPriority.MEDIUM, null, "u999",
                        null, 0, CREATED_AT, CREATED_AT
                ))
        );

        assertEquals(0L, taskMapper.countByProjectId(PROJECT_ID, null));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldAllowOnlyOneOfTwoIndependentConcurrentCasUpdates() throws Exception {
        insertBaseFixture();
        assertEquals(
                1,
                taskMapper.insert(task("t001", "Concurrent", TaskStatus.TODO, CREATED_AT))
        );

        CountDownLatch bothReadSameVersion = new CountDownLatch(2);
        CountDownLatch beginCas = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Integer> first = null;
        Future<Integer> second = null;
        try {
            first = executor.submit(() -> concurrentCasUpdate(
                    "First writer",
                    "2026-09-16T04:00:00.000Z",
                    bothReadSameVersion,
                    beginCas
            ));
            second = executor.submit(() -> concurrentCasUpdate(
                    "Second writer",
                    "2026-09-16T04:00:01.000Z",
                    bothReadSameVersion,
                    beginCas
            ));

            await(bothReadSameVersion, "两个并发事务读取相同版本");
            beginCas.countDown();
            int firstResult = first.get(20, TimeUnit.SECONDS);
            int secondResult = second.get(20, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(1, firstResult + secondResult),
                    () -> assertEquals(Set.of(0, 1), Set.of(firstResult, secondResult))
            );
            Task winner = taskMapper.findById("t001").orElseThrow();
            assertAll(
                    () -> assertEquals(1, winner.getVersion()),
                    () -> assertEquals(TaskStatus.IN_PROGRESS, winner.getStatus()),
                    () -> assertTrue(
                            Set.of("First writer", "Second writer")
                                    .contains(winner.getTitle())
                    )
            );
        } finally {
            beginCas.countDown();
            if (first != null && !first.isDone()) {
                first.cancel(true);
            }
            if (second != null && !second.isDone()) {
                second.cancel(true);
            }
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(20, TimeUnit.SECONDS),
                    "并发 CAS 测试线程应在超时前结束"
            );
            deleteBaseFixture();
        }
    }

    private int concurrentCasUpdate(
            String title,
            String changedAt,
            CountDownLatch bothReadSameVersion,
            CountDownLatch beginCas
    ) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Integer affectedRows = transaction.execute(status -> {
            Task snapshot = taskMapper.findById("t001").orElseThrow();
            int expectedVersion = snapshot.getVersion();
            assertTrue(snapshot.updateDetails(
                    title,
                    null,
                    TaskStatus.IN_PROGRESS,
                    TaskPriority.MEDIUM,
                    null,
                    null,
                    changedAt
            ));
            bothReadSameVersion.countDown();
            await(beginCas, "并发 CAS 开始信号");
            return taskMapper.update(snapshot, expectedVersion);
        });
        assertNotNull(affectedRows);
        return affectedRows;
    }

    private static void await(CountDownLatch latch, String description) {
        try {
            assertTrue(
                    latch.await(20, TimeUnit.SECONDS),
                    description + "应在超时前完成"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待" + description + "时被中断", exception);
        }
    }

    private Task task(
            String id,
            String title,
            TaskStatus status,
            String createdAt
    ) {
        return new Task(
                id,
                PROJECT_ID,
                title,
                null,
                status,
                TaskPriority.MEDIUM,
                null,
                REPORTER_ID,
                null,
                0,
                createdAt,
                createdAt
        );
    }

    private Task taskWithDue(String id, TaskStatus status, String dueAt) {
        return new Task(
                id,
                PROJECT_ID,
                "Due task " + id,
                null,
                status,
                TaskPriority.MEDIUM,
                FIRST_ASSIGNEE_ID,
                REPORTER_ID,
                dueAt,
                0,
                CREATED_AT,
                CREATED_AT
        );
    }

    private Task statisticalTask(
            String id,
            TaskStatus status,
            String assigneeId,
            String dueAt
    ) {
        return new Task(
                id,
                PROJECT_ID,
                "Statistical task " + id,
                null,
                status,
                TaskPriority.MEDIUM,
                assigneeId,
                REPORTER_ID,
                dueAt,
                0,
                CREATED_AT,
                CREATED_AT
        );
    }

    private void insertBaseFixture() {
        insertUser(REPORTER_ID, "task_reporter");
        insertUser(FIRST_ASSIGNEE_ID, "task_assignee_one");
        insertUser(SECOND_ASSIGNEE_ID, "task_assignee_two");
        insertTeam("tm001", REPORTER_ID);
        insertProject(PROJECT_ID, "tm001", REPORTER_ID);
    }

    private void insertUser(String id, String username) {
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
                        username,
                        null,
                        "ACTIVE",
                        0,
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    private void insertTeam(String id, String ownerId) {
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
                        id,
                        "Task Test Team",
                        null,
                        ownerId,
                        0,
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    private void insertProject(String id, String teamId, String creatorId) {
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
                        id,
                        teamId,
                        "Task Test Project",
                        "TASK-TEST",
                        null,
                        "ACTIVE",
                        creatorId,
                        0,
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    private void deleteBaseFixture() {
        jdbcTemplate.update("DELETE FROM tasks WHERE project_id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM teams WHERE id = ?", "tm001");
        jdbcTemplate.update(
                "DELETE FROM users WHERE id IN (?, ?, ?)",
                REPORTER_ID,
                FIRST_ASSIGNEE_ID,
                SECOND_ASSIGNEE_ID
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
            basePackageClasses = TaskMapper.class,
            annotationClass = Mapper.class
    )
    static class PersistenceTestApplication {
    }
}
