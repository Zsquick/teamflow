package com.teamflow.core.file.service;

import com.teamflow.common.validation.TextValues;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 文件存储完成后的内部结果。
 *
 * @param storageName 存储文件名
 * @param storagePath 存储位置
 * @param size 文件字节数
 * @param sha256 文件摘要
 */
public record StoredFile(String storageName, String storagePath, long size, String sha256) {

    /** 防止不完整或越界的存储结果进入业务层。 */
    public StoredFile {
        storageName = TextValues.requireNonBlank(
                storageName,
                "存储文件名"
        );
        storagePath = TextValues.requireNonBlank(
                storagePath,
                "存储相对路径"
        );
        if (size < 0) {
            throw new IllegalArgumentException("文件字节数不能为负数");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "SHA-256 必须是 64 位小写十六进制文本"
            );
        }

        Path namePath = parsePath(storageName, "存储文件名");
        if (namePath.isAbsolute()
                || namePath.getRoot() != null
                || namePath.getNameCount() != 1
                || isTraversalPart(namePath.getFileName())) {
            throw new IllegalArgumentException("存储文件名必须是单一安全名称");
        }

        Path relativePath = parsePath(storagePath, "存储相对路径");
        if (relativePath.isAbsolute() || relativePath.getRoot() != null) {
            throw new IllegalArgumentException("存储路径必须是相对路径");
        }
        for (Path part : relativePath) {
            if (isTraversalPart(part)) {
                throw new IllegalArgumentException(
                        "存储路径不能包含 . 或 .."
                );
            }
        }
        if (!relativePath.getFileName().toString().equals(storageName)) {
            throw new IllegalArgumentException(
                    "存储路径的文件名与存储文件名不一致"
            );
        }
    }

    private static Path parsePath(String value, String fieldName) {
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException(
                    fieldName + "不是有效路径",
                    exception
            );
        }
    }

    private static boolean isTraversalPart(Path part) {
        String value = part.toString();
        return value.equals(".") || value.equals("..");
    }
}
