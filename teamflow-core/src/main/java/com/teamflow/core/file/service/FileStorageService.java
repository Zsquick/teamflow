package com.teamflow.core.file.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件内容存储端口。
 */
public interface FileStorageService {

    /**
     * 流式保存文件并计算文件摘要。
     *
     * @param inputStream 文件输入流
     * @param originalName 原始文件名
     * @param expectedSize 调用方声明的文件大小
     * @return 存储结果
     * @throws IOException 文件读写失败
     * @throws FileStorageValidationException 文件超限或实际大小不一致
     *
     * <p>实现必须流式处理内容，校验实际字节数等于
     * {@code expectedSize}，且不得关闭调用方传入的输入流。</p>
     */
    StoredFile store(InputStream inputStream, String originalName, long expectedSize) throws IOException;

    /**
     * 打开已保存文件。
     *
     * @param storagePath 存储服务返回的相对存储位置
     * @return 文件输入流，由调用方关闭
     * @throws IOException 文件不存在或无法读取
     */
    InputStream open(String storagePath) throws IOException;

    /**
     * 删除已保存文件。
     *
     * @param storagePath 存储服务返回的相对存储位置
     * @throws IOException 删除失败
     *
     * <p>对不存在的文件重复删除是幂等操作。</p>
     */
    void delete(String storagePath) throws IOException;

    /**
     * 判断文件是否存在。
     *
     * @param storagePath 存储服务返回的相对存储位置
     * @return 是否存在
     *
     * <p>非法、越界或符号链接路径统一返回 {@code false}。</p>
     */
    boolean exists(String storagePath);
}
