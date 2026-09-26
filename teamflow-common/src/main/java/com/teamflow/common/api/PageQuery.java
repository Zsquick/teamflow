package com.teamflow.common.api;

/**
 * 通用分页查询参数。
 *
 * @param page 页码，从 1 开始
 * @param size 每页记录数
 */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /**
     * 校验分页参数，防止生成非法或负载过大的分页查询。
     */
    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须从 1 开始");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("每页记录数必须在 1 到 " + MAX_SIZE + " 之间");
        }
    }

    /**
     * 创建使用系统默认值的分页查询。
     *
     * @return 第 1 页、每页 20 条的分页查询
     */
    public static PageQuery defaults() {
        return new PageQuery(DEFAULT_PAGE, DEFAULT_SIZE);
    }

    /**
     * 计算数据库分页查询需要跳过的记录数。
     *
     * @return MyBatis SQL 中 OFFSET 参数的值
     */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
