package com.teamflow.core.notification.service.impl;

import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.common.error.BusinessException;
import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.transaction.TransactionCallbacks;
import com.teamflow.core.notification.domain.Notification;
import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.dto.NotificationResponse;
import com.teamflow.core.notification.error.NotificationErrorCode;
import com.teamflow.core.notification.mapper.NotificationMapper;
import com.teamflow.core.notification.service.NotificationService;
import com.teamflow.core.notification.service.NotificationUnreadCountCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** 站内通知幂等创建、分页查询和已读状态管理的默认实现。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(
            NotificationServiceImpl.class
    );

    private final NotificationMapper notificationMapper;
    private final ReadableIdGenerator idGenerator;
    private final NotificationUnreadCountCache unreadCountCache;
    private final Clock clock;

    /** 创建通知业务实现。 */
    public NotificationServiceImpl(
            NotificationMapper notificationMapper,
            ReadableIdGenerator idGenerator,
            NotificationUnreadCountCache unreadCountCache,
            Clock clock
    ) {
        this.notificationMapper = Objects.requireNonNull(
                notificationMapper,
                "通知 Mapper 不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.unreadCountCache = Objects.requireNonNull(
                unreadCountCache,
                "通知未读数缓存不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "通知时钟不能为 null");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NotificationResponse create(CreateNotificationCommand command) {
        CreateNotificationCommand validCommand = Objects.requireNonNull(
                command,
                "创建通知命令不能为 null"
        );
        Notification existing = notificationMapper.findByEventKey(
                validCommand.eventKey()
        ).orElse(null);
        if (existing != null) {
            return NotificationResponse.from(existing);
        }

        Notification notification = Notification.create(
                idGenerator.nextId(ResourceType.NOTIFICATION),
                validCommand.userId(),
                validCommand.type(),
                validCommand.title(),
                validCommand.content(),
                validCommand.eventKey(),
                UtcTimeText.now(clock)
        );
        try {
            requireSingleInsert(notificationMapper.insert(notification));
        } catch (DuplicateKeyException duplicate) {
            return notificationMapper.findByEventKey(
                            validCommand.eventKey()
                    )
                    .map(NotificationResponse::from)
                    .orElseThrow(() -> duplicate);
        }

        evictUnreadCountAfterCommit(notification.getUserId());
        return NotificationResponse.from(notification);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationResponse> listMine(
            String currentUserId,
            PageQuery pageQuery
    ) {
        String userId = requireUserId(currentUserId);
        PageQuery validPageQuery = Objects.requireNonNull(
                pageQuery,
                "分页请求不能为 null"
        );
        List<NotificationResponse> items = notificationMapper
                .findByUserId(
                        userId,
                        validPageQuery.size(),
                        validPageQuery.offset()
                )
                .stream()
                .map(NotificationResponse::from)
                .toList();
        long total = notificationMapper.countByUserId(userId);
        return PageResult.of(items, validPageQuery, total);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public long countUnread(String currentUserId) {
        String userId = requireUserId(currentUserId);
        OptionalLong cached = getCachedUnreadCount(userId);
        if (cached.isPresent()) {
            return cached.getAsLong();
        }

        long unreadCount = notificationMapper.countUnread(userId);
        if (unreadCount < 0L) {
            throw new IllegalStateException("通知未读数量不能为负数");
        }
        putCachedUnreadCount(userId, unreadCount);
        return unreadCount;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void markRead(
            String currentUserId,
            String notificationId
    ) {
        String userId = requireUserId(currentUserId);
        String id = TextValues.requireNonBlank(
                notificationId,
                "通知编号"
        );
        Notification notification = notificationMapper
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(
                        NotificationErrorCode.NOTIFICATION_NOT_FOUND
                ));
        if (notification.isRead()) {
            return;
        }

        int affectedRows = notificationMapper.markRead(
                id,
                userId,
                UtcTimeText.now(clock)
        );
        if (affectedRows < 0 || affectedRows > 1) {
            throw new IllegalStateException(
                    "标记通知已读时受影响行数只能为 0 或 1"
            );
        }

        // 0 表示并发请求已先完成相同更新，仍属于幂等成功。
        evictUnreadCountAfterCommit(userId);
    }

    private OptionalLong getCachedUnreadCount(String userId) {
        try {
            OptionalLong cached = Objects.requireNonNull(
                    unreadCountCache.get(userId),
                    "通知未读数缓存查询结果不能为 null"
            );
            if (cached.isEmpty() || cached.getAsLong() >= 0L) {
                return cached;
            }
            log.warn("忽略非法通知未读数缓存 userId={}", userId);
            evictUnreadCount(userId);
        } catch (RuntimeException cacheFailure) {
            log.warn(
                    "读取通知未读数缓存失败，回退数据库 userId={} failureType={}",
                    userId,
                    cacheFailure.getClass().getName()
            );
        }
        return OptionalLong.empty();
    }

    private void putCachedUnreadCount(String userId, long unreadCount) {
        try {
            unreadCountCache.put(userId, unreadCount);
        } catch (RuntimeException cacheFailure) {
            log.warn(
                    "写入通知未读数缓存失败 userId={} failureType={}",
                    userId,
                    cacheFailure.getClass().getName()
            );
        }
    }

    private void evictUnreadCountAfterCommit(String userId) {
        TransactionCallbacks.afterCommit(() -> evictUnreadCount(userId));
    }

    private void evictUnreadCount(String userId) {
        try {
            unreadCountCache.evict(userId);
        } catch (RuntimeException cacheFailure) {
            log.warn(
                    "删除通知未读数缓存失败 userId={} failureType={}",
                    userId,
                    cacheFailure.getClass().getName()
            );
        }
    }

    private static String requireUserId(String userId) {
        return TextValues.requireNonBlank(userId, "当前用户编号");
    }

    private static void requireSingleInsert(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增通知时受影响行数必须为 1"
            );
        }
    }
}
