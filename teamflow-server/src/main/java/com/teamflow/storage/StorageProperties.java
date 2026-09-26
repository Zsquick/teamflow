package com.teamflow.storage;

import java.nio.file.Path;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** 本地文件存储配置。 */
@ConfigurationProperties("teamflow.storage")
public record StorageProperties(Path root, DataSize maxFileSize) {

    /** 校验并固定存储实现使用的绝对根目录。 */
    public StorageProperties {
        Objects.requireNonNull(root, "存储根目录不能为 null");
        Objects.requireNonNull(
                maxFileSize,
                "文件大小上限不能为 null"
        );
        if (root.toString().isBlank()) {
            throw new IllegalArgumentException("存储根目录不能为空路径");
        }
        if (maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("文件大小上限必须大于 0");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (normalizedRoot.getParent() == null) {
            throw new IllegalArgumentException(
                    "存储根目录不能直接使用文件系统根目录"
            );
        }
        root = normalizedRoot;
    }
}
