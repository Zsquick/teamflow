package com.teamflow.core.notification.service;

import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.notification.domain.Notification;
import com.teamflow.core.notification.domain.NotificationType;
import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.dto.NotificationResponse;
import com.teamflow.core.notification.error.NotificationErrorCode;
import com.teamflow.core.notification.mapper.NotificationMapper;
import com.teamflow.core.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 通知幂等创建、分页、缓存和已读状态的关键业务测试。 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final String USER_ID = "u001";
    private static final String NOTIFICATION_ID = "n001";
    private static final String EVENT_KEY =
            "TASK_ASSIGNED:t001:1:ASSIGNEE_CHANGED";
    private static final String NOW = "2026-09-18T01:02:03.456Z";

    @Mock
    private NotificationMapper mapper;
    @Mock
    private ReadableIdGenerator idGenerator;
    @Mock
    private NotificationUnreadCountCache cache;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(
                mapper,
                idGenerator,
                cache,
                Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReturnExistingNotificationForSameEvent() {
        Notification existing = notification(false, null);
        when(mapper.findByEventKey(EVENT_KEY))
                .thenReturn(Optional.of(existing));

        NotificationResponse response = service.create(command());

        assertEquals(NOTIFICATION_ID, response.id());
        verifyNoInteractions(idGenerator, cache);
        verify(mapper, never()).insert(any(Notification.class));
    }

    @Test
    void shouldCreateUnreadNotificationAndEvictCachedCount() {
        when(mapper.findByEventKey(EVENT_KEY)).thenReturn(Optional.empty());
        when(idGenerator.nextId(ResourceType.NOTIFICATION))
                .thenReturn(NOTIFICATION_ID);
        when(mapper.insert(any(Notification.class))).thenReturn(1);

        NotificationResponse response = service.create(command());

        assertEquals(NOTIFICATION_ID, response.id());
        assertEquals(false, response.read());
        verify(cache).evict(USER_ID);
    }

    @Test
    void shouldRecoverExistingNotificationAfterConcurrentDuplicate() {
        Notification existing = notification(false, null);
        when(mapper.findByEventKey(EVENT_KEY))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(idGenerator.nextId(ResourceType.NOTIFICATION))
                .thenReturn("n002");
        when(mapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        NotificationResponse response = service.create(command());

        assertEquals(NOTIFICATION_ID, response.id());
        verifyNoInteractions(cache);
    }

    @Test
    void shouldListOnlyCurrentUsersStablePage() {
        PageQuery query = new PageQuery(2, 20);
        when(mapper.findByUserId(USER_ID, 20, 20L))
                .thenReturn(List.of(notification(false, null)));
        when(mapper.countByUserId(USER_ID)).thenReturn(21L);

        PageResult<NotificationResponse> result = service.listMine(
                USER_ID,
                query
        );

        assertEquals(1, result.items().size());
        assertEquals(2, result.page());
        assertEquals(21L, result.total());
    }

    @Test
    void shouldUseUnreadCacheAndFillItOnMiss() {
        when(cache.get(USER_ID))
                .thenReturn(OptionalLong.of(3L), OptionalLong.empty());
        assertEquals(3L, service.countUnread(USER_ID));
        verify(mapper, never()).countUnread(USER_ID);

        when(mapper.countUnread(USER_ID)).thenReturn(4L);
        assertEquals(4L, service.countUnread(USER_ID));
        verify(cache).put(USER_ID, 4L);
    }

    @Test
    void shouldMarkUnreadNotificationAndKeepRepeatedCallIdempotent() {
        when(mapper.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .thenReturn(
                        Optional.of(notification(false, null)),
                        Optional.of(notification(true, NOW))
                );
        when(mapper.markRead(NOTIFICATION_ID, USER_ID, NOW))
                .thenReturn(1);

        service.markRead(USER_ID, NOTIFICATION_ID);
        service.markRead(USER_ID, NOTIFICATION_ID);

        verify(mapper).markRead(NOTIFICATION_ID, USER_ID, NOW);
        verify(cache).evict(USER_ID);
    }

    @Test
    void shouldHideAnotherUsersNotificationAsNotFound() {
        when(mapper.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.markRead(USER_ID, NOTIFICATION_ID)
        );

        assertSame(
                NotificationErrorCode.NOTIFICATION_NOT_FOUND,
                exception.getErrorCode()
        );
        verify(mapper, never()).markRead(
                anyString(),
                anyString(),
                anyString()
        );
    }

    private CreateNotificationCommand command() {
        return new CreateNotificationCommand(
                USER_ID,
                NotificationType.TASK_ASSIGNED,
                "任务指派",
                "任务 t001 已指派给你",
                EVENT_KEY
        );
    }

    private Notification notification(boolean read, String readAt) {
        return new Notification(
                NOTIFICATION_ID,
                USER_ID,
                NotificationType.TASK_ASSIGNED,
                "任务指派",
                "任务 t001 已指派给你",
                EVENT_KEY,
                read,
                NOW,
                readAt
        );
    }
}
