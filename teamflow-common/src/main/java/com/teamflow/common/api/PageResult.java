package com.teamflow.common.api;

import com.teamflow.common.validation.NumberValues;

import java.util.List;
import java.util.Objects;

/**
 * 通用分页结果。
 *
 * @param items 当前页数据
 * @param page 当前页码
 * @param size 每页记录数
 * @param total 总记录数
 * @param <T> 数据类型
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    /**
     * 校验分页元数据，并保存当前页数据的不可变副本。
     */
    public PageResult {
        Objects.requireNonNull(items, "分页数据不能为 null");
        items = List.copyOf(items);

        if (page < 1) {
            throw new IllegalArgumentException("页码必须从 1 开始");
        }
        if (size < 1 || size > PageQuery.MAX_SIZE) {
            throw new IllegalArgumentException(
                    "每页记录数必须在 1 到 " + PageQuery.MAX_SIZE + " 之间"
            );
        }
        NumberValues.requireNonNegative(total, "总记录数");
        if (items.size() > size) {
            throw new IllegalArgumentException("当前页记录数不能超过每页记录数");
        }
    }

    /**
     * 根据分页请求和查询结果创建分页响应。
     *
     * @param items 当前页数据
     * @param query 本次分页请求
     * @param total 符合查询条件的总记录数
     * @param <T> 数据类型
     * @return 分页响应
     */
    public static <T> PageResult<T> of(List<T> items, PageQuery query, long total) {
        Objects.requireNonNull(query, "分页请求不能为 null");
        return new PageResult<>(items, query.page(), query.size(), total);
    }

    /**
     * 创建没有任何记录的分页响应。
     *
     * @param query 本次分页请求
     * @param <T> 数据类型
     * @return 空分页响应
     */
    public static <T> PageResult<T> empty(PageQuery query) {
        return of(List.of(), query, 0L);
    }

    /**
     * 计算总页数。
     *
     * @return 没有记录时返回 0，否则返回向上取整后的总页数
     */
    public long totalPages() {
        return total == 0 ? 0 : (total - 1) / size + 1;
    }

    /**
     * 判断当前结果之前是否还有有效页面。
     *
     * @return 有上一页时返回 true
     */
    public boolean hasPrevious() {
        return total > 0 && page > 1;
    }

    /**
     * 判断当前结果之后是否还有有效页面。
     *
     * @return 有下一页时返回 true
     */
    public boolean hasNext() {
        return page < totalPages();
    }
}
