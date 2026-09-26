package com.teamflow.core.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 附件下载所需的文件流和元数据。
 *
 * @param originalName 原始文件名
 * @param contentType 媒体类型
 * @param size 文件字节数
 * @param streamOpener 延迟打开文件流的回调
 */
public record AttachmentDownload(
        String originalName,
        String contentType,
        long size,
        StreamOpener streamOpener
) {

    /** 允许流式响应在真正开始执行时再占用文件句柄。 */
    @FunctionalInterface
    public interface StreamOpener {
        InputStream open() throws IOException;
    }

    public AttachmentDownload {
        Objects.requireNonNull(originalName, "原始文件名不能为 null");
        Objects.requireNonNull(contentType, "媒体类型不能为 null");
        Objects.requireNonNull(streamOpener, "附件流打开器不能为 null");
        if (size < 1L) {
            throw new IllegalArgumentException("附件大小必须大于 0");
        }
    }

    /** 打开一次新的下载流，流由调用方关闭。 */
    public InputStream openStream() throws IOException {
        return Objects.requireNonNull(
                streamOpener.open(),
                "附件流打开器不能返回 null"
        );
    }
}
