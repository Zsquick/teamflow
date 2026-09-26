package com.teamflow.core.user.mapper;

import com.teamflow.core.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Optional;

/**
 * 用户数据访问接口。
 */
@Mapper
public interface UserMapper {

    /**
     * 新增用户。
     *
     * @param user 待保存用户
     * @return 受影响行数
     */
    int insert(User user);

    /**
     * 按标识查询用户。
     *
     * @param id 用户标识
     * @return 用户，可为空
     */
    Optional<User> findById(@Param("id") String id);

    /**
     * 按用户名或邮箱查询用户。
     *
     * @param identifier 用户名或邮箱
     * @return 用户，可为空
     */
    Optional<User> findByIdentifier(@Param("identifier") String identifier);

    /**
     * 判断指定用户编号是否存在，不加载密码摘要等完整用户资料。
     *
     * @param id 用户编号
     * @return 匹配的用户数量
     */
    long countById(@Param("id") String id);

    /**
     * 判断用户名是否存在。
     *
     * @param username 用户名
     * @return 是否存在
     */
    long countByUsername(@Param("username") String username);

    /**
     * 判断邮箱是否存在。
     *
     * @param email 邮箱
     * @return 是否存在
     */
    long countByEmail(@Param("email") String email);

    /**
     * 根据乐观锁版本修改用户。
     *
     * @param user 待修改用户
     * @param expectedVersion 调用方读取数据时的版本号
     * @return 受影响行数
     */
    int update(
            @Param("user") User user,
            @Param("expectedVersion") int expectedVersion
    );
}
