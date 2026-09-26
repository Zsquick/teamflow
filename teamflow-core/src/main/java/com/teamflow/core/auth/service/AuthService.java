package com.teamflow.core.auth.service;

import com.teamflow.core.auth.dto.LoginRequest;
import com.teamflow.core.auth.dto.RefreshTokenRequest;
import com.teamflow.core.auth.dto.RegisterRequest;
import com.teamflow.core.auth.dto.TokenResponse;
import com.teamflow.core.user.dto.UserResponse;

/**
 * 用户认证业务契约。
 */
public interface AuthService {

    /**
     * 注册新用户。
     *
     * @param request 注册信息
     * @return 新用户信息
     */
    UserResponse register(RegisterRequest request);

    /**
     * 校验凭据并签发令牌。
     *
     * @param request 登录信息
     * @return 访问令牌和刷新令牌
     */
    TokenResponse login(LoginRequest request);

    /**
     * 使用刷新令牌签发新令牌。
     *
     * @param request 刷新请求
     * @return 新令牌
     */
    TokenResponse refresh(RefreshTokenRequest request);

    /**
     * 注销刷新令牌和当前登录状态。
     *
     * @param userId 形如 {@code u001} 的当前用户编号
     * @param refreshToken 刷新令牌
     */
    void logout(String userId, String refreshToken);
}
