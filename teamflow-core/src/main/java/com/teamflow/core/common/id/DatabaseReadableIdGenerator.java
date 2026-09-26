package com.teamflow.core.common.id;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 基于数据库序列行生成可读资源编号。
 */
@Service
public class DatabaseReadableIdGenerator implements ReadableIdGenerator {

    private final IdSequenceMapper idSequenceMapper;

    public DatabaseReadableIdGenerator(IdSequenceMapper idSequenceMapper) {
        this.idSequenceMapper = Objects.requireNonNull(
                idSequenceMapper,
                "编号序列 Mapper 不能为 null"
        );
    }

    /**
     * 在同一个事务中锁定序列、读取当前值、推进序列并格式化编号。
     *
     * @param resourceType 资源类型
     * @return 新分配的可读资源编号
     */
    @Override
    @Transactional
    public String nextId(ResourceType resourceType) {
        Objects.requireNonNull(resourceType, "资源类型不能为 null");

        Integer currentValue = idSequenceMapper.findNextValueForUpdate(
                resourceType.sequenceName()
        );
        if (currentValue == null) {
            throw new IllegalStateException(
                    "未配置编号序列: " + resourceType.sequenceName()
            );
        }
        if (currentValue < 1) {
            throw new IllegalStateException(
                    "编号序列值必须是正整数: " + resourceType.sequenceName()
            );
        }

        final int nextValue;
        try {
            nextValue = Math.addExact(currentValue, 1);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "编号序列已达到整数上限: " + resourceType.sequenceName(),
                    exception
            );
        }

        int affectedRows = idSequenceMapper.updateNextValue(
                resourceType.sequenceName(),
                nextValue
        );
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "编号序列更新结果异常: " + resourceType.sequenceName()
            );
        }

        return resourceType.format(currentValue);
    }
}
