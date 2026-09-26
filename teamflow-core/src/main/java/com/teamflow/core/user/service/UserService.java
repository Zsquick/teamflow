package com.teamflow.core.user.service;

import com.teamflow.core.user.dto.UserResponse;

/**
 * 用户资料业务契约。
 */
public interface UserService {

    /**
     * 查询当前登录用户资料。
     *
     * @param currentUserId 当前用户标识
     * @return 用户资料
     */
    UserResponse getCurrentUser(String currentUserId);
}
