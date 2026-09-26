package com.teamflow.core.file.domain;

import com.teamflow.common.validation.NumberValues;
import com.teamflow.common.validation.TextValues;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.file.service.StoredFile;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 任务附件元数据，只描述物理文件而不在数据库中保存文件内容。
 */
public class Attachment {

    public static final String DEFAULT_CONTENT_TYPE =
            "application/octet-stream";

    private static final int MAX_ID_LENGTH = 32;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;
    private static final int MAX_STORAGE_NAME_LENGTH = 64;
    private static final int MAX_STORAGE_PATH_LENGTH = 512;
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    private static final Pattern SHA_256_PATTERN = Pattern.compile(
            "[0-9a-f]{64}"
    );

    private final String id;
    private final String taskId;
    private final String uploaderId;
    private final String originalName;
    private final String storageName;
    private final String storagePath;
    private final String contentType;
    private final long size;
    private final String sha256;
    private final String createdAt;

    /** 使用持久化字段还原附件元数据。 */
    public Attachment(
            String id,
            String taskId,
            String uploaderId,
            String originalName,
            String storageName,
            String storagePath,
            String contentType,
            long size,
            String sha256,
            String createdAt
    ) {
        this.id = requireSized(id, "附件编号", MAX_ID_LENGTH);
        this.taskId = requireSized(taskId, "任务编号", MAX_ID_LENGTH);
        this.uploaderId = requireSized(
                uploaderId,
                "上传者编号",
                MAX_ID_LENGTH
        );
        this.originalName = requireSafeOriginalName(originalName);
        this.storageName = requireSized(
                storageName,
                "存储文件名",
                MAX_STORAGE_NAME_LENGTH
        );
        this.storagePath = requireSized(
                storagePath,
                "存储路径",
                MAX_STORAGE_PATH_LENGTH
        );
        this.contentType = requireSafeContentType(contentType);
        this.size = requirePositiveSize(size);
        this.sha256 = requireSha256(sha256);
        this.createdAt = UtcTimeText.requireValid(
                createdAt,
                "附件创建时间"
        );
    }

    /** 根据已完成的物理存储结果创建附件元数据。 */
    public static Attachment create(
            String id,
            String taskId,
            String uploaderId,
            String originalName,
            String contentType,
            StoredFile storedFile,
            String now
    ) {
        StoredFile validStoredFile = Objects.requireNonNull(
                storedFile,
                "存储结果不能为 null"
        );
        return new Attachment(
                id,
                taskId,
                uploaderId,
                normalizeOriginalName(originalName),
                validStoredFile.storageName(),
                validStoredFile.storagePath(),
                normalizeContentType(contentType),
                validStoredFile.size(),
                validStoredFile.sha256(),
                now
        );
    }

    /** 在执行文件 I/O 前规范化用户提供的原始文件名。 */
    public static String normalizeOriginalName(String originalName) {
        String value = TextValues.requireNonBlank(
                originalName,
                "原始文件名"
        ).strip();
        int separatorIndex = Math.max(
                value.lastIndexOf('/'),
                value.lastIndexOf('\\')
        );
        String baseName = value.substring(separatorIndex + 1).strip();
        return requireSafeOriginalName(baseName);
    }

    /** 将缺失的媒体类型统一为安全的二进制下载类型。 */
    public static String normalizeContentType(String contentType) {
        String value = TextValues.stripToNull(contentType);
        return requireSafeContentType(
                value == null ? DEFAULT_CONTENT_TYPE : value
        );
    }

    /** 要求附件包含至少一个字节。 */
    public static long requirePositiveSize(long size) {
        NumberValues.requireNonNegative(size, "附件大小");
        if (size == 0L) {
            throw new IllegalArgumentException("附件内容不能为空");
        }
        return size;
    }

    private static String requireSafeOriginalName(String originalName) {
        String value = requireSized(
                originalName,
                "原始文件名",
                MAX_ORIGINAL_NAME_LENGTH
        );
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("原始文件名不能包含路径分隔符");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("原始文件名不能包含控制字符");
        }
        return value;
    }

    private static String requireSafeContentType(String contentType) {
        String value = requireSized(
                contentType,
                "媒体类型",
                MAX_CONTENT_TYPE_LENGTH
        );
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("媒体类型不能包含控制字符");
        }
        return value;
    }

    private static String requireSha256(String sha256) {
        String value = TextValues.requireNonBlank(sha256, "SHA-256 摘要");
        if (!SHA_256_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "SHA-256 摘要必须是 64 位小写十六进制文本"
            );
        }
        return value;
    }

    private static String requireSized(
            String value,
            String fieldName,
            int maxLength
    ) {
        String valid = TextValues.requireNonBlank(value, fieldName);
        if (valid.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过 " + maxLength + " 个字符"
            );
        }
        return valid;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStorageName() {
        return storageName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getSha256() {
        return sha256;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
