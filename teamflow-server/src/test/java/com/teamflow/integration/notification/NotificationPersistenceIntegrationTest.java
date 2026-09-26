package com.teamflow.integration.notification;

import com.teamflow.core.notification.domain.Notification;
import com.teamflow.core.notification.domain.NotificationType;
import com.teamflow.core.notification.mapper.NotificationMapper;
import org.apache.ibatis.annotations.Mapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 MySQL 8.4 验证 V8 和通知 Mapper 的隔离、幂等与分页。 */
@SpringBootTest(
        classes = NotificationPersistenceIntegrationTest.PersistenceTestApplication.class,
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
class NotificationPersistenceIntegrationTest {

    private static final String USER_ID = "u001";
    private static final String OTHER_USER_ID = "u002";
    private static final String T1 = "2026-09-18T01:00:00.000Z";
    private static final String T2 = "2026-09-18T02:00:00.000Z";
    private static final String T3 = "2026-09-18T03:00:00.000Z";
    private static final String READ_AT = "2026-09-18T04:00:00.000Z";

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(com.teamflow.integration.TestContainerImages.MYSQL)
                    .withDatabaseName("teamflow_notification_test")
                    .withUsername("teamflow_notification_test")
                    .withPassword("teamflow_notification_test_password");

    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NotificationMapper notificationMapper;

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

    @BeforeEach
    void insertUsers() {
        insertUser(USER_ID, "alice");
        insertUser(OTHER_USER_ID, "bob");
    }

    @Test
    void shouldApplyV8AndRoundTripNotification() {
        assertTrue(Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .anyMatch(MigrationVersion.fromVersion("8")::equals));
        Notification expected = notification(
                "n001", USER_ID, "event-001", T1
        );

        assertEquals(1, notificationMapper.insert(expected));
        Notification actual = notificationMapper.findByEventKey("event-001")
                .orElseThrow();

        assertAll(
                () -> assertEquals("n001", actual.getId()),
                () -> assertEquals(USER_ID, actual.getUserId()),
                () -> assertEquals(
                        NotificationType.TASK_ASSIGNED,
                        actual.getType()
                ),
                () -> assertEquals("通知标题", actual.getTitle()),
                () -> assertEquals("通知内容", actual.getContent()),
                () -> assertFalse(actual.isRead()),
                () -> assertEquals(T1, actual.getCreatedAt())
        );
    }

    @Test
    void shouldEnforceEventKeyIdempotencyInDatabase() {
        notificationMapper.insert(notification(
                "n001", USER_ID, "same-event", T1
        ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> notificationMapper.insert(notification(
                        "n002", OTHER_USER_ID, "same-event", T2
                ))
        );
    }

    @Test
    void shouldPageOneUsersNotificationsInStableDescendingOrder() {
        notificationMapper.insert(notification(
                "n001", USER_ID, "event-001", T1
        ));
        notificationMapper.insert(notification(
                "n002", USER_ID, "event-002", T2
        ));
        notificationMapper.insert(notification(
                "n003", USER_ID, "event-003", T3
        ));
        notificationMapper.insert(notification(
                "n004", OTHER_USER_ID, "event-004", T3
        ));

        List<Notification> page = notificationMapper.findByUserId(
                USER_ID,
                2,
                1
        );

        assertAll(
                () -> assertEquals(
                        List.of("n002", "n001"),
                        page.stream().map(Notification::getId).toList()
                ),
                () -> assertEquals(3, notificationMapper.countByUserId(USER_ID)),
                () -> assertEquals(3, notificationMapper.countUnread(USER_ID))
        );
    }

    @Test
    void shouldMarkReadOnlyForOwnerAndRemainIdempotent() {
        notificationMapper.insert(notification(
                "n001", USER_ID, "event-001", T1
        ));

        assertAll(
                () -> assertEquals(
                        0,
                        notificationMapper.markRead(
                                "n001", OTHER_USER_ID, READ_AT
                        )
                ),
                () -> assertEquals(
                        1,
                        notificationMapper.markRead(
                                "n001", USER_ID, READ_AT
                        )
                ),
                () -> assertEquals(
                        0,
                        notificationMapper.markRead(
                                "n001", USER_ID, READ_AT
                        )
                ),
                () -> assertEquals(0, notificationMapper.countUnread(USER_ID)),
                () -> assertTrue(notificationMapper.findByIdAndUserId(
                        "n001",
                        USER_ID
                ).orElseThrow().isRead())
        );
    }

    private static Notification notification(
            String id,
            String userId,
            String eventKey,
            String createdAt
    ) {
        return Notification.create(
                id,
                userId,
                NotificationType.TASK_ASSIGNED,
                "通知标题",
                "通知内容",
                eventKey,
                createdAt
        );
    }

    private void insertUser(String id, String username) {
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, username, email, password_hash, display_name,
                    avatar_url, status, version, created_at, updated_at
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
                T1,
                T1
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
            basePackageClasses = NotificationMapper.class,
            annotationClass = Mapper.class
    )
    static class PersistenceTestApplication {
    }
}
