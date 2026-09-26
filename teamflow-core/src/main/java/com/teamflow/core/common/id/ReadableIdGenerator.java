package com.teamflow.core.common.id;

/**
 * 生成可读资源编号的应用层契约。
 */
@FunctionalInterface
public interface ReadableIdGenerator {

    /**
     * 为指定资源分配一个尚未使用的编号。
     *
     * @param resourceType 资源类型
     * @return 可读资源编号
     */
    String nextId(ResourceType resourceType);
}
