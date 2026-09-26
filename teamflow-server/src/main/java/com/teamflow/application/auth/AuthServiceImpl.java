package com.teamflow.application.auth;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.auth.error.AuthErrorCode;
import com.teamflow.core.auth.dto.LoginRequest;
import com.teamflow.core.auth.dto.RefreshTokenRequest;
import com.teamflow.core.auth.dto.RegisterRequest;
import com.teamflow.core.auth.dto.TokenResponse;
import com.teamflow.core.auth.service.AuthService;
import com.teamflow.core.auth.support.LoginIdentity;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.core.user.mapper.UserMapper;
import com.teamflow.security.AuthenticatedUser;
import com.teamflow.security.AuthenticationFailureTranslator;
import com.teamflow.security.JwtProperties;
import com.teamflow.security.JwtTokenService;
import com.teamflow.security.ParsedRefreshToken;
import com.teamflow.security.RefreshTokenStore;
import com.teamflow.security.TeamFlowUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * 用户注册、登录和刷新令牌实现。
 */
@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final ReadableIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final AuthenticationFailureTranslator authenticationFailureTranslator;

    /**
     * 创建认证业务实现。
     *
     * @param userMapper 用户数据访问接口
     * @param idGenerator 可读编号生成器
     * @param passwordEncoder 密码编码器
     * @param clock 项目统一时钟
     * @param jwtTokenService JWT 服务
     * @param refreshTokenStore 刷新令牌存储
     * @param jwtProperties JWT 配置
     * @param authenticationManager Spring Security 认证管理器
     * @param authenticationFailureTranslator 认证失败转换器
     */
    public AuthServiceImpl(
            UserMapper userMapper,
            ReadableIdGenerator idGenerator,
            PasswordEncoder passwordEncoder,
            Clock clock,
            JwtTokenService jwtTokenService,
            RefreshTokenStore refreshTokenStore,
            JwtProperties jwtProperties,
            AuthenticationManager authenticationManager,
            AuthenticationFailureTranslator authenticationFailureTranslator
    ) {
        this.userMapper = Objects.requireNonNull(userMapper, "用户 Mapper 不能为 null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "编号生成器不能为 null");
        this.passwordEncoder = Objects.requireNonNull(
                passwordEncoder,
                "密码编码器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "项目时钟不能为 null");
        this.jwtTokenService = Objects.requireNonNull(
                jwtTokenService,
                "JWT 服务不能为 null"
        );
        this.refreshTokenStore = Objects.requireNonNull(
                refreshTokenStore,
                "刷新令牌存储不能为 null"
        );
        this.jwtProperties = Objects.requireNonNull(
                jwtProperties,
                "JWT 配置不能为 null"
        );
        this.authenticationManager = Objects.requireNonNull(
                authenticationManager,
                "认证管理器不能为 null"
        );
        this.authenticationFailureTranslator = Objects.requireNonNull(
                authenticationFailureTranslator,
                "认证失败转换器不能为 null"
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        Objects.requireNonNull(request, "注册请求不能为 null");

        String username = LoginIdentity.normalize(request.username());
        String email = LoginIdentity.normalize(request.email());

        if (userMapper.countByUsername(username) > 0) {
            throw new BusinessException(AuthErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userMapper.countByEmail(email) > 0) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // BCrypt 成本较高，先完成哈希，避免取得编号序列行锁后长时间占锁。
        String passwordHash = passwordEncoder.encode(request.password());
        String userId = idGenerator.nextId(ResourceType.USER);
        String now = UtcTimeText.now(clock);
        User user = User.create(
                userId,
                username,
                email,
                passwordHash,
                request.displayName(),
                now
        );

        try {
            int affectedRows = userMapper.insert(user);
            if (affectedRows != 1) {
                throw new IllegalStateException("新增用户时受影响行数必须为 1");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(AuthErrorCode.REGISTRATION_CONFLICT);
        }

        return UserResponse.from(user);
    }

    /** {@inheritDoc} */
    @Override
    public TokenResponse login(LoginRequest request) {
        Objects.requireNonNull(request, "登录请求不能为 null");

        Authentication authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.identifier(),
                        request.password()
                );
        final Authentication authenticationResult;
        try {
            authenticationResult = authenticationManager.authenticate(
                    authenticationRequest
            );
        } catch (AuthenticationException exception) {
            throw authenticationFailureTranslator.translate(exception);
        }

        if (!(authenticationResult.getPrincipal()
                instanceof TeamFlowUserDetails userDetails)) {
            throw new IllegalStateException(
                    "认证成功结果必须包含 TeamFlowUserDetails"
            );
        }

        AuthenticatedUser user = userDetails.toAuthenticatedUser();
        TokenResponse response = issueTokens(user);
        refreshTokenStore.save(
                user.id(),
                response.refreshToken(),
                jwtProperties.refreshTokenTtl()
        );
        return response;
    }

    /** {@inheritDoc} */
    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        Objects.requireNonNull(request, "刷新请求不能为 null");

        String currentToken = request.refreshToken();
        ParsedRefreshToken parsedToken = parseRefreshToken(currentToken);
        User user = userMapper.findById(parsedToken.userId())
                .orElseThrow(() -> new BusinessException(
                        AuthErrorCode.REFRESH_TOKEN_INVALID
                ));
        AuthenticatedUser authenticatedUser =
                toActiveAuthenticatedUser(user, currentToken);
        TokenResponse response = issueTokens(authenticatedUser);

        boolean rotated = refreshTokenStore.rotate(
                authenticatedUser.id(),
                currentToken,
                response.refreshToken(),
                jwtProperties.refreshTokenTtl()
        );
        if (!rotated) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return response;
    }

    /** {@inheritDoc} */
    @Override
    public void logout(String userId, String refreshToken) {
        refreshTokenStore.delete(userId, refreshToken);
    }

    private ParsedRefreshToken parseRefreshToken(String token) {
        try {
            Claims claims = jwtTokenService.parse(token);
            return jwtTokenService.toRefreshToken(claims);
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private AuthenticatedUser toActiveAuthenticatedUser(
            User user,
            String currentToken
    ) {
        AuthErrorCode statusError = switch (user.getStatus()) {
            case ACTIVE -> null;
            case DISABLED -> AuthErrorCode.ACCOUNT_DISABLED;
            case LOCKED -> AuthErrorCode.ACCOUNT_LOCKED;
        };
        if (statusError != null) {
            refreshTokenStore.delete(user.getId(), currentToken);
            throw new BusinessException(statusError);
        }

        TeamFlowUserDetails userDetails = TeamFlowUserDetails.from(user);
        try {
            return userDetails.toAuthenticatedUser();
        } finally {
            userDetails.eraseCredentials();
        }
    }

    private TokenResponse issueTokens(AuthenticatedUser user) {
        String accessToken = jwtTokenService.createAccessToken(user);
        String refreshToken = jwtTokenService.createRefreshToken(user);
        return TokenResponse.bearer(
                accessToken,
                refreshToken,
                jwtProperties.accessTokenTtl().toSeconds()
        );
    }
}
