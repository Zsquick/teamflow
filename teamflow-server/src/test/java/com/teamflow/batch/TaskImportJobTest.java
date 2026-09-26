package com.teamflow.batch;

import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.file.service.FileStorageService;
import com.teamflow.core.file.service.StoredFile;
import com.teamflow.core.importjob.domain.ImportJob;
import com.teamflow.core.importjob.domain.ImportJobStatus;
import com.teamflow.core.importjob.mapper.ImportJobMapper;
import com.teamflow.metrics.TeamFlowMetrics;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 使用真实 MySQL 验证 V9/V10、CSV skip 策略和业务状态回写。 */
@SpringBatchTest
@SpringBootTest(
        classes = TaskImportJobTest.BatchTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.batch.job.enabled=false",
                "spring.batch.jdbc.initialize-schema=never",
                "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
                "mybatis.configuration.map-underscore-to-camel-case=true"
        }
)
@Testcontainers(disabledWithoutDocker = true)
class TaskImportJobTest {

    private static final String NOW = "2026-09-19T08:00:00.000Z";
    private static final String USER_ID = "u901";
    private static final String TEAM_ID = "tm901";
    private static final String MEMBER_ID = "mb901";
    private static final String PROJECT_ID = "p901";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(com.teamflow.integration.TestContainerImages.MYSQL)
                    .withDatabaseName("teamflow_batch_test")
                    .withUsername("teamflow_batch_test")
                    .withPassword("teamflow_batch_test_password");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ImportJobMapper importJobMapper;

    @Autowired
    private InMemoryFileStorage storage;

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                MYSQL::getDriverClassName
        );
    }

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM import_jobs");
        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", PROJECT_ID);
        jdbcTemplate.update("DELETE FROM team_members WHERE id = ?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM teams WHERE id = ?", TEAM_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
        storage.clear();
        insertFixture();
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    void shouldImportValidRowsAndSkipConfiguredInvalidRows() throws Exception {
        String importJobId = "ij901";
        String path = "batch/valid-and-invalid.csv";
        storage.put(
                path,
                "title,description,priority,assigneeEmail,dueAt\n"
                        + "First task,Description,HIGH,,"
                        + "2026-10-01T00:00:00.000Z\n"
                        + "Invalid priority,,UNKNOWN,,\n"
                        + "Too,few\n"
                        + "x".repeat(201)
                        + ",,LOW,,\n"
                        + "Second task,,LOW,,\n"
        );
        insertPendingImport(importJobId, path);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                parameters(importJobId, path, 901L)
        );

        ImportJob result = importJobMapper.findById(importJobId).orElseThrow();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE project_id = ?",
                Long.class,
                PROJECT_ID
        ));
        assertEquals(ImportJobStatus.COMPLETED, result.getStatus());
        assertEquals(5L, result.getTotalRows());
        assertEquals(2L, result.getSuccessRows());
        assertEquals(3L, result.getFailedRows());
        assertNotNull(result.getStartedAt());
        assertNotNull(result.getFinishedAt());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_INSTANCE",
                Long.class
        ));

        assertThrows(
                Exception.class,
                () -> jobLauncherTestUtils.launchJob(
                        parameters(importJobId, path, 901L)
                )
        );
    }

    @Test
    void shouldCommitLargeFileInBoundedChunks() throws Exception {
        String importJobId = "ij903";
        String path = "batch/more-than-two-chunks.csv";
        StringBuilder csv = new StringBuilder(
                "title,description,priority,assigneeEmail,dueAt\n"
        );
        int rowCount = TaskImportJobConfig.CHUNK_SIZE * 2 + 5;
        for (int index = 1; index <= rowCount; index++) {
            csv.append("Task ")
                    .append(index)
                    .append(",,MEDIUM,,\n");
        }
        storage.put(path, csv.toString());
        insertPendingImport(importJobId, path);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                parameters(importJobId, path, 903L)
        );

        ImportJob result = importJobMapper.findById(importJobId).orElseThrow();
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(rowCount, result.getTotalRows());
        assertEquals(rowCount, result.getSuccessRows());
        assertEquals(0L, result.getFailedRows());
        assertEquals(rowCount, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE project_id = ?",
                Long.class,
                PROJECT_ID
        ));
        assertTrue(jdbcTemplate.queryForObject(
                """
                SELECT commit_count
                FROM BATCH_STEP_EXECUTION
                WHERE job_execution_id = ?
                """,
                Long.class,
                execution.getId()
        ) >= 3L);
    }

    @Test
    void shouldFailWhenSkipLimitIsExceeded() throws Exception {
        String importJobId = "ij902";
        String path = "batch/too-many-invalid.csv";
        StringBuilder csv = new StringBuilder(
                "title,description,priority,assigneeEmail,dueAt\n"
        );
        for (int index = 0; index <= TaskImportJobConfig.SKIP_LIMIT; index++) {
            csv.append("Invalid ")
                    .append(index)
                    .append(",,UNKNOWN,,\n");
        }
        storage.put(path, csv.toString());
        insertPendingImport(importJobId, path);

        JobExecution execution = jobLauncherTestUtils.launchJob(
                parameters(importJobId, path, 902L)
        );

        ImportJob result = importJobMapper.findById(importJobId).orElseThrow();
        assertEquals(BatchStatus.FAILED, execution.getStatus());
        assertEquals(ImportJobStatus.FAILED, result.getStatus());
        assertEquals(0L, result.getSuccessRows());
        assertTrue(result.getFailedRows() > 0L);
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE project_id = ?",
                Long.class,
                PROJECT_ID
        ));
    }

    private JobParameters parameters(
            String importJobId,
            String path,
            long requestTime
    ) {
        return new JobParametersBuilder()
                .addString(TaskImportJobConfig.PARAM_IMPORT_JOB_ID, importJobId)
                .addString(TaskImportJobConfig.PARAM_PROJECT_ID, PROJECT_ID)
                .addString(TaskImportJobConfig.PARAM_REPORTER_ID, USER_ID)
                .addString(TaskImportJobConfig.PARAM_STORAGE_PATH, path)
                .addLong(TaskImportJobConfig.PARAM_REQUEST_TIME, requestTime)
                .toJobParameters();
    }

    private void insertPendingImport(String importJobId, String path) {
        assertEquals(1, importJobMapper.insert(ImportJob.create(
                importJobId,
                PROJECT_ID,
                USER_ID,
                "tasks.csv",
                path,
                NOW
        )));
    }

    private void insertFixture() {
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, username, email, password_hash, display_name,
                    avatar_url, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                USER_ID,
                "batch_user",
                "batch@example.com",
                "test-password-hash",
                "Batch User",
                null,
                "ACTIVE",
                0,
                NOW,
                NOW
        );
        jdbcTemplate.update(
                """
                INSERT INTO teams (
                    id, name, description, owner_id, version,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                TEAM_ID,
                "Batch Team",
                null,
                USER_ID,
                0,
                NOW,
                NOW
        );
        jdbcTemplate.update(
                """
                INSERT INTO team_members (
                    id, team_id, user_id, member_role, joined_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
                MEMBER_ID,
                TEAM_ID,
                USER_ID,
                "OWNER",
                NOW
        );
        jdbcTemplate.update(
                """
                INSERT INTO projects (
                    id, team_id, name, project_key, description,
                    project_status, created_by, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                PROJECT_ID,
                TEAM_ID,
                "Batch Project",
                "BATCH",
                null,
                "ACTIVE",
                USER_ID,
                0,
                NOW,
                NOW
        );
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            MybatisAutoConfiguration.class,
            BatchAutoConfiguration.class
    })
    @EnableTransactionManagement
    @MapperScan(
            basePackages = "com.teamflow.core",
            annotationClass = Mapper.class
    )
    @Import({TaskImportJobConfig.class, TaskCsvReaderFactory.class})
    static class BatchTestApplication {

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-09-19T08:00:00Z"),
                    ZoneOffset.UTC
            );
        }

        @Bean
        InMemoryFileStorage fileStorageService() {
            return new InMemoryFileStorage();
        }

        @Bean
        ReadableIdGenerator readableIdGenerator() {
            AtomicInteger sequence = new AtomicInteger(1);
            return resourceType -> resourceType.format(
                    sequence.getAndIncrement()
            );
        }

        @Bean
        TeamFlowMetrics teamFlowMetrics() {
            return mock(TeamFlowMetrics.class);
        }
    }

    static final class InMemoryFileStorage implements FileStorageService {
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();
        private final AtomicInteger names = new AtomicInteger();

        void put(String path, String content) {
            files.put(path, content.getBytes(StandardCharsets.UTF_8));
        }

        void clear() {
            files.clear();
        }

        @Override
        public StoredFile store(
                InputStream inputStream,
                String originalName,
                long expectedSize
        ) throws IOException {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length != expectedSize) {
                throw new IOException("测试存储声明大小不一致");
            }
            String name = "stored" + names.incrementAndGet();
            String path = "batch/" + name;
            files.put(path, bytes);
            return new StoredFile(name, path, bytes.length, sha256(bytes));
        }

        @Override
        public InputStream open(String storagePath) throws IOException {
            byte[] bytes = files.get(storagePath);
            if (bytes == null) {
                throw new IOException("测试文件不存在: " + storagePath);
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void delete(String storagePath) {
            files.remove(storagePath);
        }

        @Override
        public boolean exists(String storagePath) {
            return files.containsKey(storagePath);
        }

        private static String sha256(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(bytes)
                );
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
