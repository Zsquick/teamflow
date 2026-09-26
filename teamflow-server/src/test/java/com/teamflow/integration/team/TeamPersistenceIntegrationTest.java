package com.teamflow.integration.team;

import com.teamflow.core.common.id.DatabaseReadableIdGenerator;
import com.teamflow.core.common.id.IdSequenceMapper;
import com.teamflow.core.team.domain.Team;
import com.teamflow.core.team.domain.TeamMember;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.dto.CreateTeamRequest;
import com.teamflow.core.team.dto.TeamMemberResponse;
import com.teamflow.core.team.dto.TeamResponse;
import com.teamflow.core.team.mapper.TeamMapper;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.team.service.TeamService;
import com.teamflow.core.team.service.impl.TeamAuthorizationServiceImpl;
import com.teamflow.core.team.service.impl.TeamServiceImpl;
import com.teamflow.core.user.mapper.UserMapper;
import org.apache.ibatis.annotations.Mapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 验证团队持久化映射和数据库基础约束。
 */
@SpringBootTest(
        classes = TeamPersistenceIntegrationTest.PersistenceTestApplication.class,
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
class TeamPersistenceIntegrationTest {

    private static final String CREATED_AT = "2026-09-13T01:02:03.456Z";
    private static final String JOINED_AT = "2026-09-13T02:03:04.567Z";
    private static final int ROLLBACK_SEQUENCE_VALUE = 900_001;
    private static final String ROLLBACK_USER_ID = "u900001";
    private static final String ROLLBACK_SEED_TEAM_ID = "tm999999";
    private static final String ROLLBACK_NEW_TEAM_ID = "tm900001";
    private static final String ROLLBACK_MEMBER_ID = "mb900001";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(com.teamflow.integration.TestContainerImages.MYSQL)
                    .withDatabaseName("teamflow_test")
                    .withUsername("teamflow_test")
                    .withPassword("teamflow_test_password");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TeamMapper teamMapper;

    @Autowired
    private TeamMemberMapper teamMemberMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeamService teamService;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Test
    void shouldApplyV3AndRoundTripReadableIdsAndUtcTextsThroughMyBatis() {
        assertTrue(
                Arrays.stream(flyway.info().applied())
                        .map(MigrationInfo::getVersion)
                        .anyMatch(MigrationVersion.fromVersion("3")::equals)
        );
        insertUser("u001", "owner");
        assertEquals(1L, userMapper.countById("u001"));
        assertEquals(0L, userMapper.countById("u999"));

        Team team = Team.create(
                "tm001",
                "Platform Team",
                "Builds the shared platform",
                "u001",
                CREATED_AT
        );
        TeamMember owner = TeamMember.create(
                "mb001",
                "tm001",
                "u001",
                TeamRole.OWNER,
                JOINED_AT
        );

        assertEquals(1, teamMapper.insert(team));
        assertEquals(1, teamMemberMapper.insert(owner));

        Team reloadedTeam = teamMapper.findById("tm001").orElseThrow();
        TeamMember reloadedOwner = teamMemberMapper
                .findByTeamAndUser("tm001", "u001")
                .orElseThrow();
        List<TeamResponse> joinedTeams = teamMapper.findByUserId("u001");
        List<TeamMemberResponse> memberDetails = teamMemberMapper
                .findDetailsByTeamId("tm001");

        assertAll(
                () -> assertEquals("tm001", reloadedTeam.getId()),
                () -> assertEquals("u001", reloadedTeam.getOwnerId()),
                () -> assertEquals(CREATED_AT, reloadedTeam.getCreatedAt()),
                () -> assertEquals(CREATED_AT, reloadedTeam.getUpdatedAt()),
                () -> assertEquals("mb001", reloadedOwner.getId()),
                () -> assertEquals("tm001", reloadedOwner.getTeamId()),
                () -> assertEquals("u001", reloadedOwner.getUserId()),
                () -> assertEquals(TeamRole.OWNER, reloadedOwner.getRole()),
                () -> assertEquals(JOINED_AT, reloadedOwner.getJoinedAt()),
                () -> assertEquals(1, joinedTeams.size()),
                () -> assertEquals("tm001", joinedTeams.getFirst().id()),
                () -> assertEquals(
                        TeamRole.OWNER,
                        joinedTeams.getFirst().currentUserRole()
                ),
                () -> assertEquals(1, memberDetails.size()),
                () -> assertEquals("u001", memberDetails.getFirst().userId()),
                () -> assertEquals(
                        "owner",
                        memberDetails.getFirst().username()
                ),
                () -> assertEquals(
                        TeamRole.OWNER,
                        memberDetails.getFirst().role()
                )
        );
    }

    @Test
    void shouldUpdateTeamOnlyWhenExpectedVersionMatches() {
        insertUser("u001", "owner");
        insertTeam("tm001", "u001");
        Team team = teamMapper.findById("tm001").orElseThrow();
        int expectedVersion = team.getVersion();
        String changedAt = "2026-09-13T03:04:05.678Z";

        assertTrue(team.updateDetails(
                "Updated Platform Team",
                "Updated description",
                changedAt
        ));
        assertEquals(0, teamMapper.update(team, expectedVersion + 1));

        Team unchanged = teamMapper.findById("tm001").orElseThrow();
        assertAll(
                () -> assertEquals("Platform Team", unchanged.getName()),
                () -> assertEquals(0, unchanged.getVersion()),
                () -> assertEquals(CREATED_AT, unchanged.getUpdatedAt())
        );

        assertEquals(1, teamMapper.update(team, expectedVersion));
        Team updated = teamMapper.findById("tm001").orElseThrow();
        assertAll(
                () -> assertEquals("Updated Platform Team", updated.getName()),
                () -> assertEquals("Updated description", updated.getDescription()),
                () -> assertEquals(1, updated.getVersion()),
                () -> assertEquals(changedAt, updated.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectDuplicateTeamAndUserMembership() {
        insertUser("u001", "owner");
        insertTeam("tm001", "u001");

        assertEquals(
                1,
                teamMemberMapper.insert(TeamMember.create(
                        "mb001",
                        "tm001",
                        "u001",
                        TeamRole.OWNER,
                        JOINED_AT
                ))
        );

        assertThrows(
                DuplicateKeyException.class,
                () -> teamMemberMapper.insert(TeamMember.create(
                        "mb002",
                        "tm001",
                        "u001",
                        TeamRole.MEMBER,
                        "2026-09-13T02:04:05.678Z"
                ))
        );
    }

    @Test
    void shouldLockRequestedMembersInStableUserOrder() {
        insertUser("u001", "owner");
        insertUser("u002", "admin");
        insertUser("u003", "outsider");
        insertTeam("tm001", "u001");
        assertEquals(
                1,
                teamMemberMapper.insert(TeamMember.create(
                        "mb001",
                        "tm001",
                        "u001",
                        TeamRole.OWNER,
                        JOINED_AT
                ))
        );
        assertEquals(
                1,
                teamMemberMapper.insert(TeamMember.create(
                        "mb002",
                        "tm001",
                        "u002",
                        TeamRole.ADMIN,
                        "2026-09-13T02:04:05.678Z"
                ))
        );

        List<TeamMember> lockedMembers =
                teamMemberMapper.findByTeamAndUsersForUpdate(
                        "tm001",
                        List.of("u003", "u002", "u001")
                );

        assertEquals(
                List.of("u001", "u002"),
                lockedMembers.stream().map(TeamMember::getUserId).toList()
        );
    }

    @Test
    void shouldUseExpectedRoleForConditionalUpdateAndDelete() {
        insertUser("u001", "owner");
        insertUser("u002", "member");
        insertTeam("tm001", "u001");
        assertEquals(
                1,
                teamMemberMapper.insert(TeamMember.create(
                        "mb002",
                        "tm001",
                        "u002",
                        TeamRole.ADMIN,
                        JOINED_AT
                ))
        );

        assertEquals(
                0,
                teamMemberMapper.updateRole(
                        "tm001",
                        "u002",
                        TeamRole.MEMBER,
                        TeamRole.OWNER
                )
        );
        assertEquals(
                TeamRole.ADMIN,
                teamMemberMapper.findByTeamAndUser("tm001", "u002")
                        .orElseThrow()
                        .getRole()
        );

        assertEquals(
                1,
                teamMemberMapper.updateRole(
                        "tm001",
                        "u002",
                        TeamRole.ADMIN,
                        TeamRole.MEMBER
                )
        );
        assertEquals(
                TeamRole.MEMBER,
                teamMemberMapper.findByTeamAndUser("tm001", "u002")
                        .orElseThrow()
                        .getRole()
        );

        assertEquals(
                0,
                teamMemberMapper.delete(
                        "tm001",
                        "u002",
                        TeamRole.ADMIN
                )
        );
        assertTrue(
                teamMemberMapper.findByTeamAndUser("tm001", "u002").isPresent()
        );

        assertEquals(
                1,
                teamMemberMapper.delete(
                        "tm001",
                        "u002",
                        TeamRole.MEMBER
                )
        );
        assertFalse(
                teamMemberMapper.findByTeamAndUser("tm001", "u002").isPresent()
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollBackTeamAndBothSequencesWhenOwnerMembershipInsertFails() {
        int originalTeamSequence = readSequenceValue("TEAM");
        int originalMemberSequence = readSequenceValue("TEAM_MEMBER");

        try {
            setSequenceValue("TEAM", ROLLBACK_SEQUENCE_VALUE);
            setSequenceValue("TEAM_MEMBER", ROLLBACK_SEQUENCE_VALUE);
            insertUser(ROLLBACK_USER_ID, "rollback_owner");
            insertTeam(ROLLBACK_SEED_TEAM_ID, ROLLBACK_USER_ID);
            assertEquals(
                    1,
                    teamMemberMapper.insert(TeamMember.create(
                            ROLLBACK_MEMBER_ID,
                            ROLLBACK_SEED_TEAM_ID,
                            ROLLBACK_USER_ID,
                            TeamRole.OWNER,
                            JOINED_AT
                    ))
            );

            assertThrows(
                    DuplicateKeyException.class,
                    () -> teamService.create(
                            ROLLBACK_USER_ID,
                            new CreateTeamRequest("Rollback Team", null)
                    )
            );

            assertAll(
                    () -> assertTrue(
                            teamMapper.findById(ROLLBACK_NEW_TEAM_ID).isEmpty()
                    ),
                    () -> assertEquals(
                            ROLLBACK_SEQUENCE_VALUE,
                            readSequenceValue("TEAM")
                    ),
                    () -> assertEquals(
                            ROLLBACK_SEQUENCE_VALUE,
                            readSequenceValue("TEAM_MEMBER")
                    )
            );
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM team_members WHERE team_id = ?",
                    ROLLBACK_SEED_TEAM_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM team_members WHERE team_id = ?",
                    ROLLBACK_NEW_TEAM_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM teams WHERE id = ? OR id = ?",
                    ROLLBACK_SEED_TEAM_ID,
                    ROLLBACK_NEW_TEAM_ID
            );
            jdbcTemplate.update(
                    "DELETE FROM users WHERE id = ?",
                    ROLLBACK_USER_ID
            );
            setSequenceValue("TEAM", originalTeamSequence);
            setSequenceValue("TEAM_MEMBER", originalMemberSequence);
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
        int affectedRows = jdbcTemplate.update(
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
        );
        assertEquals(1, affectedRows);
    }

    private void insertTeam(String teamId, String ownerId) {
        assertEquals(
                1,
                teamMapper.insert(Team.create(
                        teamId,
                        "Platform Team",
                        null,
                        ownerId,
                        CREATED_AT
                ))
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
                    TeamMapper.class,
                    UserMapper.class,
                    IdSequenceMapper.class
            },
            annotationClass = Mapper.class
    )
    @Import({
            DatabaseReadableIdGenerator.class,
            TeamAuthorizationServiceImpl.class,
            TeamServiceImpl.class
    })
    static class PersistenceTestApplication {

        @Bean
        Clock clock() {
            return Clock.fixed(
                    Instant.parse("2026-09-13T03:04:05.678Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
