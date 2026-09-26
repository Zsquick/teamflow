package com.teamflow.security;

import com.teamflow.core.auth.support.LoginIdentity;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.mapper.UserMapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 按登录标识读取 TeamFlow 用户，并构造 Spring Security 用户详情。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * 创建用户详情服务。
     *
     * @param userMapper 用户数据访问接口
     */
    public CustomUserDetailsService(UserMapper userMapper) {
        this.userMapper = Objects.requireNonNull(
                userMapper,
                "用户 Mapper 不能为 null"
        );
    }

    /**
     * 按用户名或邮箱加载认证用户。
     *
     * <p>本方法只负责读取认证所需的数据，不在这里验证密码，也不提前拒绝
     * 停用或锁定账号。密码和状态由 Spring Security 的认证流程统一处理。</p>
     *
     * @param identifier 用户名或邮箱
     * @return Spring Security 用户详情
     * @throws UsernameNotFoundException 用户不存在
     */
    @Override
    @Transactional(readOnly = true)
    public TeamFlowUserDetails loadUserByUsername(String identifier)
            throws UsernameNotFoundException {
        String normalizedIdentifier = LoginIdentity.normalize(identifier);
        User user = userMapper.findByIdentifier(normalizedIdentifier)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "登录标识或密码错误"
                ));

        return TeamFlowUserDetails.from(user);
    }
}
