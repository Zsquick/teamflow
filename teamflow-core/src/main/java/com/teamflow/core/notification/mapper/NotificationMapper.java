package com.teamflow.core.notification.mapper;

import com.teamflow.core.notification.domain.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 站内通知数据访问接口。
 */
@Mapper
public interface NotificationMapper {

    /**
     * 新增通知。
     *
     * @param notification 待保存通知
     * @return 受影响行数
     */
    int insert(Notification notification);

    /**
     * 按消息事件幂等键查询通知。
     *
     * @param eventKey 事件唯一键
     * @return 已创建通知，可为空
     */
    Optional<Notification> findByEventKey(
            @Param("eventKey") String eventKey
    );

    /**
     * 按通知标识和收件用户查询通知。
     *
     * @param id 通知标识
     * @param userId 收件用户标识
     * @return 当前用户可见的通知，可为空
     */
    Optional<Notification> findByIdAndUserId(
            @Param("id") String id,
            @Param("userId") String userId
    );

    /**
     * 分页查询用户通知。
     *
     * @param userId 用户标识
     * @param limit 最大返回数量
     * @param offset 跳过的记录数
     * @return 通知列表
     */
    List<Notification> findByUserId(
            @Param("userId") String userId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    /** 统计用户的全部通知数量。 */
    long countByUserId(@Param("userId") String userId);

    /**
     * 统计用户未读通知。
     *
     * @param userId 用户标识
     * @return 未读数量
     */
    long countUnread(@Param("userId") String userId);

    /**
     * 将指定通知标记为已读。
     *
     * @param id 通知标识
     * @param userId 用户标识
     * @param readAt 已读时间
     * @return 受影响行数
     */
    int markRead(
            @Param("id") String id,
            @Param("userId") String userId,
            @Param("readAt") String readAt
    );
}
