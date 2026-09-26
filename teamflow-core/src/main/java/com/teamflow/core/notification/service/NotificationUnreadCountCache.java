package com.teamflow.core.notification.service;

import java.util.OptionalLong;

/**
 * 通知未读数量缓存端口。
 *
 * <p>缓存实现和过期时间属于基础设施职责；数据库计数始终是最终事实。</p>
 */
public interface NotificationUnreadCountCache {

    /** 查询用户已缓存的未读数量。 */
    OptionalLong get(String userId);

    /** 保存用户未读数量。 */
    void put(String userId, long unreadCount);

    /** 删除用户未读数量缓存。 */
    void evict(String userId);
}
