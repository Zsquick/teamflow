package com.teamflow.integration.project;

import com.teamflow.core.common.id.DatabaseReadableIdGenerator;
import com.teamflow.core.common.id.IdSequenceMapper;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.dto.CreateProjectRequest;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.project.service.ProjectService;
import com.teamflow.core.project.service.impl.ProjectServiceImpl;
import com.teamflow.core.team.domain.Team;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.mapper.TeamMapper;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.team.service.impl.TeamAuthorizationServiceImpl;
import org.apache.ibatis.annotations.Mapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 验证项目迁移、MyBatis 映射、基础约束和事务边界。
 */
@SpringBootTest(
        classes = ProjectPersistenceIntegrationTest.PersistenceTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.datasource.hikari.maximum-pool-size=2",
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
class ProjectPersistenceIntegrationTest {

    private static final String CREATED_AT = "2026-09-15T01:02:03.456Z";
    private static final String LATER_AT = "2026-09-15T03:04:05.678Z";
    private static final String SERVICE_NOW = "2026-09-15T05:06:07.890Z";

    private static final int ROLLBACK_SEQUENCE_VALUE = 900_001;
    private static final String ROLLBACK_USER_ID = "u900001";
    private static final String ROLLBACK_TEAM_ID = "tm900001";
    private static final String ROLLBACK_MEMBER_ID = "mb900001";
    private static final String ROLLBACK_PROJECT_ID = "p900001";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(com.teamflow.integration.TestContainerImages.MYSQL)
                    .withDatabaseName("teamflow_project_test")
                    .withUsername("teamflow_project_test")
                    .withPassword("teamflow_project_test_password");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("projectMapper")
    private ProjectMapper projectMapper;

    @Autowired
    private TeamMapper teamMapper;

    @Autowired
    private TeamMemberMapper teamMemberMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private FailureAfterInsertProjectMapper failureProjectMapper;

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
    void shouldApplyV4AndRoundTripReadableIdsAndUtcTextsThroughMyBatis() {
        assertTrue(
                Arrays.stream(flyway.info().applied())
                        .map(MigrationInfo::getVersion)
                        .anyMatch(MigrationVersion.fromVersion("4")::equals)
        );
        insertUser("u001", "project_owner");
        insertTeam("tm001", "u001");

        Project project = Project.create(
                "p001",
                "tm001",
                "  TeamFlow API  ",
                "tf-api",
                "  项目持久化测试  ",
                "u001",
                CREATED_AT
        );

        assertEquals(1, projectMapper.insert(project));
        Project reloaded = projectMapper.findById("p001").orElseThrow();

        assertAll(
                () -> assertEquals("p001", reloaded.getId()),
                () -> assertEquals("tm001", reloaded.getTeamId()),
                () -> assertEquals("TeamFlow API", reloaded.getName()),
                () -> assertEquals("TF-API", reloaded.getProjectKey()),
                () -> assertEquals("项目持久化测试", reloaded.getDescription()),
                () -> assertEquals(ProjectStatus.ACTIVE, reloaded.getStatus()),
                () -> assertEquals("u001", reloaded.getCreatedBy()),
                () -> assertEquals(0, reloaded.getVersion()),
                () -> assertEquals(CREATED_AT, reloaded.getCreatedAt()),
                () -> assertEquals(CREATED_AT, reloaded.getUpdatedAt()),
                () -> assertEquals(
                        1,
                        projectMapper.countByTeamIdAndProjectKey(
                                "tm001",
                                "TF-API"
                        )
                )
        );
    }

    @Test
    void shouldListProjectsInCreatedAtThenIdOrder() {
        insertUser("u001", "sorting_owner");
        insertTeam("tm001", "u001");

        insertProject(
                "p003",
                "tm001",
                "Third",
                "THREE",
                LATER_AT
        );
        insertProject(
                "p002",
                "tm001",
                "Second",
                "TWO",
                CREATED_AT
        );
        insertProject(
                "p001",
                "tm001",
                "First",
                "ONE",
                CREATED_AT
        );

        List<String> orderedIds = projectMapper.findByTeamId("tm001")
                .stream()
                .map(Project::getId)
                .toList();

        assertEquals(List.of("p001", "p002", "p003"), orderedIds);
    }

    @Test
    void shouldKeepProjectKeyUniqueWithinTeamButAllowItAcrossTeams() {
        insertUser("u001", "unique_owner");
        insertTeam("tm001", "u001");
        insertTeam("tm002", "u001");

        insertProject(
                "p001",
                "tm001",
                "First Team Project",
                "SHARED",
                CREATED_AT
        );
        insertProject(
                "p002",
                "tm002",
                "Second Team Project",
                "SHARED",
                CREATED_AT
        );

        assertAll(
                () -> assertEquals(
                        1,
                        projectMapper.countByTeamIdAndProjectKey(
                                "tm001",
                                "SHARED"
                        )
                ),
                () -> assertEquals(
                        1,
                        projectMapper.countByTeamIdAndProjectKey(
                                "tm002",
                                "SHARED"
                        )
                )
        );
        assertThrows(
                DuplicateKeyException.class,
                () -> insertProject(
                        "p003",
                        "tm001",
                        "Duplicate Project",
                        "SHARED",
                        LATER_AT
                )
        );
    }

    @Test
    void shouldUpdateProjectOnlyWhenExpectedVersionMatches() {
        insertUser("u001", "locking_owner");
        insertTeam("tm001", "u001");
        insertProject(
                "p001",
                "tm001",
                "Original Project",
                "LOCK",
                CREATED_AT
        );
        Project project = projectMapper.findById("p001").orElseThrow();
        int expectedVersion = project.getVersion();

        assertTrue(project.updateDetails(
                "Archived Project",
                "Updated description",
                ProjectStatus.ARCHIVED,
                LATER_AT
        ));
        assertEquals(
                0,
                projectMapper.update(project, expectedVersion + 1)
        );

        Project unchanged = projectMapper.findById("p001").orElseThrow();
        assertAll(
                () -> assertEquals("Original Project", unchanged.getName()),
                () -> assertEquals(ProjectStatus.ACTIVE, unchanged.getStatus()),
                () -> assertEquals(0, unchanged.getVersion()),
                () -> assertEquals(CREATED_AT, unchanged.getUpdatedAt())
        );

        assertEquals(1, projectMapper.update(project, expectedVersion));
        Project updated = projectMapper.findById("p001").orElseThrow();
        assertAll(
                () -> assertEquals("Archived Project", updated.getName()),
                () -> assertEquals(
                        "Updated description",
                        updated.getDescription()
                ),
                () -> assertEquals(ProjectStatus.ARCHIVED, updated.getStatus()),
                () -> assertEquals(1, updated.getVersion()),
                () -> assertEquals(LATER_AT, updated.getUpdatedAt())
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollBackInsertedProjectAndSequenceWhenCreateFailsAfterInsert() {
        int originalProjectSequence = readSequenceValue("PROJECT");

        try {
            setSequenceValue("PROJECT", ROLLBACK_SEQUENCE_VALUE);
            insertUser(ROLLBACK_USER_ID, "rollback_project_owner");
            insertTeam(ROLLBACK_TEAM_ID, ROLLBACK_USER_ID);
            assertEquals(
                    1,
                    teamMemberMapper.insert(TeamMember.create(
                            ROLLBACK_MEMBER_ID,
                            ROLLBACK_TEAM_ID,
                            ROLLBACK_USER_ID,
                            TeamRole.OWNER,
                            CREATED_AT
                    ))
            );
            failureProjectMapper.failAfterNextSuccessfulInsert();

            assertThrows(
                    SimulatedPostInsertFailure.class,
                    () -> projectService.create(
                            ROLLBACK_USER_ID,
                            new CreateProjectRequest(
                                    ROLLBACK_TEAM_ID,
                                    "Rollback Project",
                                    "rollback-test",
                                    null
                            )
                    )
            );

            assertAll(
                    () -> assertTrue(
                            projectMapper.findById(ROLLBACK_PROJECT_ID)
                                    .isEmpty()
                    ),
                    () -> assertEquals(
                            ROLLBACK_SEQUENCE_VALUE,
                            readSequenceValue("PROJECT")
                    )
            );
        } finally {
            failureProjectMapper.clearFailure();
            jdbcTemplate.update(
                    "DELETE FROM projects WHERE id = ?",
                    ROLLBACK_PROJECT_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM team_members WHERE team_id = ?",
                    ROLLBACK_TEAM_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM teams WHERE id = ?",
                    ROLLBACK_TEAM_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    ROLLBACK_USER_ID
            );
            setSequenceValue("PROJECT", originalProjectSequence);
        }
    }

    private int readSequenceValue(String sequenceName) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT next_value FROM id_sequences WHERE sequence_name = ?",
                Integer.class,
                sequenceName
        );
        if (value == null) {
            throw new IllegalStateException(
                    "Missing test sequence: " + sequenceName
            );
        }
        return value;
    }

    private void setSequenceValue(String sequenceName, int nextValue) {
        assertEquals(
                1,
                jdbcTemplate.update(
                        "UPDATE id_sequences SET next_value = ? "
                                + "WHERE sequence_name = ?",
                        nextValue,
                        sequenceName
                )
        );
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

    private void insertTeam(String teamId, String ownerId) {
        assertEquals(
                1,
                teamMapper.insert(Team.create(
                        teamId,
                        "Project Test Team",
                        null,
                        ownerId,
                        CREATED_AT
                ))
        );
    }

    private void insertProject(
            String projectId,
            String teamId,
            String name,
            String projectKey,
            String createdAt
    ) {
        assertEquals(
                1,
                projectMapper.insert(Project.create(
                        projectId,
                        teamId,
                        name,
                        projectKey,
                        null,
                        "u001",
                        createdAt
                ))
        );
    }

    /**
     * 在真实 INSERT 成功后注入异常，用于观察外层业务事务能否回滚数据库
     * 记录和同一事务中已经推进的可读编号序列。
     */
    static final class FailureAfterInsertProjectMapper
            implements ProjectMapper {

        private final ProjectMapper delegate;
        private final AtomicBoolean failAfterInsert = new AtomicBoolean();

        FailureAfterInsertProjectMapper(ProjectMapper delegate) {
            this.delegate = delegate;
        }

        void failAfterNextSuccessfulInsert() {
            failAfterInsert.set(true);
        }

        void clearFailure() {
            failAfterInsert.set(false);
        }

        @Override
        public int insert(Project project) {
            int affectedRows = delegate.insert(project);
            if (affectedRows == 1 && failAfterInsert.compareAndSet(true, false)) {
                throw new SimulatedPostInsertFailure();
            }
            return affectedRows;
        }

        @Override
        public Optional<Project> findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<Project> findByIdForUpdate(String id) {
            return delegate.findByIdForUpdate(id);
        }

        @Override
        public int countByTeamIdAndProjectKey(
                String teamId,
                String projectKey
        ) {
            return delegate.countByTeamIdAndProjectKey(teamId, projectKey);
        }

        @Override
        public List<Project> findByTeamId(String teamId) {
            return delegate.findByTeamId(teamId);
        }

        @Override
        public int update(Project project, int expectedVersion) {
            return delegate.update(project, expectedVersion);
        }
    }

    static final class SimulatedPostInsertFailure extends RuntimeException {
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
                    ProjectMapper.class,
                    TeamMapper.class,
                    IdSequenceMapper.class
            },
            annotationClass = Mapper.class
    )
    @Import({
            DatabaseReadableIdGenerator.class,
            TeamAuthorizationServiceImpl.class,
            ProjectServiceImpl.class
    })
    static class PersistenceTestApplication {

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse(SERVICE_NOW),
                    ZoneOffset.UTC
            );
        }

        @Bean
        @Primary
        FailureAfterInsertProjectMapper failureAfterInsertProjectMapper(
                @Qualifier("projectMapper") ProjectMapper delegate
        ) {
            return new FailureAfterInsertProjectMapper(delegate);
        }
    }
}
