package com.teamflow.core.user.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.common.error.CommonErrorCode;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.dto.UserResponse;
import com.teamflow.core.user.mapper.UserMapper;
import com.teamflow.core.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 用户资料业务默认实现。
 */
@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;

    /**
     * 创建用户资料业务实现。
     *
     * @param userMapper 用户数据访问接口
     */
    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = Objects.requireNonNull(userMapper, "用户 Mapper 不能为 null");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String currentUserId) {
        User user = userMapper.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.RESOURCE_NOT_FOUND
                ));

        return UserResponse.from(user);
    }
}
