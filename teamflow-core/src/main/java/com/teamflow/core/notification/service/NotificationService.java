package com.teamflow.core.notification.service;

import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.core.notification.dto.CreateNotificationCommand;
import com.teamflow.core.notification.dto.NotificationResponse;

/**
 * 站内通知业务契约。
 */
public interface NotificationService {

    /**
     * 幂等创建一条站内通知。
     *
     * @param command 通知内容和来源事件键
     * @return 已存在或新创建的通知
     */
    NotificationResponse create(CreateNotificationCommand command);

    /**
     * 分页查询当前用户通知。
     *
     * @param currentUserId 当前用户标识
     * @param pageQuery 分页参数
     * @return 分页通知
     */
    PageResult<NotificationResponse> listMine(
            String currentUserId,
            PageQuery pageQuery
    );

    /**
     * 查询当前用户未读数量。
     *
     * @param currentUserId 当前用户标识
     * @return 未读数量
     */
    long countUnread(String currentUserId);

    /**
     * 将通知标记为已读。
     *
     * @param currentUserId 当前用户标识
     * @param notificationId 通知标识
     */
    void markRead(String currentUserId, String notificationId);
}
