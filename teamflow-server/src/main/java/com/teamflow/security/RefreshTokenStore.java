package com.teamflow.security;

import java.time.Duration;

/**
 * 刷新令牌状态存储契约。
 *
 * <p>当前采用单设备会话策略：每个用户只保留一个有效刷新令牌，保存新令牌会
 * 覆盖同一用户的旧令牌。实现不得持久化刷新令牌原文。</p>
 */
public interface RefreshTokenStore {

    /**
     * 保存刷新令牌状态，并使用给定有效期覆盖该用户的旧状态。
     *
     * @param userId 形如 {@code u001} 的用户编号
     * @param token 刷新令牌
     * @param ttl 有效期
     */
    void save(String userId, String token, Duration ttl);

    /**
     * 当前令牌匹配时，以原子操作替换为新令牌并重置有效期。
     *
     * <p>同一个旧令牌即使被并发提交，也只能有一个请求成功完成轮换。</p>
     *
     * @param userId 形如 {@code u001} 的用户编号
     * @param currentToken 当前刷新令牌
     * @param newToken 新刷新令牌
     * @param ttl 新刷新令牌有效期
     * @return 是否成功完成轮换
     */
    boolean rotate(
            String userId,
            String currentToken,
            String newToken,
            Duration ttl
    );

    /**
     * 仅在刷新令牌与当前状态匹配时删除状态。
     *
     * <p>令牌不存在或不匹配时不执行删除，重复调用不会产生额外影响。</p>
     *
     * @param userId 形如 {@code u001} 的用户编号
     * @param token 刷新令牌
     */
    void delete(String userId, String token);
}
