package com.teamflow.storage;

import com.teamflow.core.file.service.FileStorageService;
import com.teamflow.core.file.service.FileStorageValidationException;
import com.teamflow.core.file.service.StoredFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * 基于 Java NIO 的本地流式文件存储。
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final int BUFFER_SIZE = 16 * 1024;
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final Path root;
    private final long maxFileSize;

    public LocalFileStorageService(StorageProperties properties) {
        StorageProperties validProperties = Objects.requireNonNull(
                properties,
                "存储配置不能为 null"
        );
        this.maxFileSize = validProperties.maxFileSize().toBytes();
        try {
            Files.createDirectories(validProperties.root());
            this.root = validProperties.root().toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "无法创建或访问存储根目录",
                    exception
            );
        }
    }

    @Override
    public StoredFile store(
            InputStream inputStream,
            String originalName,
            long expectedSize
    ) throws IOException {
        Objects.requireNonNull(inputStream, "文件输入流不能为 null");
        Objects.requireNonNull(originalName, "原始文件名不能为 null");
        if (originalName.isBlank()) {
            throw new IllegalArgumentException("原始文件名不能为空");
        }
        if (expectedSize < 0) {
            throw new IllegalArgumentException("声明文件大小不能为负数");
        }
        if (expectedSize > maxFileSize) {
            throw validationFailure(
                    FileStorageValidationException.Reason.TOO_LARGE,
                    "声明文件大小超过存储上限"
            );
        }

        String storageName = UUID.randomUUID()
                .toString()
                .replace("-", "");
        Path relativePath = Path.of(
                storageName.substring(0, 2),
                storageName.substring(2, 4),
                storageName
        );
        Path target = root.resolve(relativePath).normalize();
        requireInsideRoot(target);
        Path directory = target.getParent();
        createSafeDirectories(directory);

        Path temporaryFile = null;
        boolean moved = false;
        try {
            temporaryFile = Files.createTempFile(
                    directory,
                    "." + storageName + "-",
                    ".part"
            );
            CopyResult copyResult = copyAndDigest(
                    inputStream,
                    temporaryFile,
                    expectedSize
            );
            try {
                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "当前文件系统不支持存储文件的原子移动",
                        exception
                );
            }
            moved = true;
            String portablePath = relativePath.toString()
                    .replace('\\', '/');
            return new StoredFile(
                    storageName,
                    portablePath,
                    copyResult.size(),
                    copyResult.sha256()
            );
        } finally {
            if (!moved && temporaryFile != null) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    @Override
    public InputStream open(String storagePath) throws IOException {
        Path target = resolveStoredPath(storagePath);
        requireSafeExistingPath(target, false);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(storagePath);
        }
        SeekableByteChannel channel = Files.newByteChannel(
                target,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        );
        return new BufferedInputStream(Channels.newInputStream(channel));
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Path target = resolveStoredPath(storagePath);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            requireSafeExistingParents(target);
            return;
        }
        requireSafeExistingPath(target, false);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("存储目标不是普通文件");
        }
        Files.deleteIfExists(target);
    }

    @Override
    public boolean exists(String storagePath) {
        try {
            Path target = resolveStoredPath(storagePath);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireSafeExistingParents(target);
                return false;
            }
            requireSafeExistingPath(target, false);
            return Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private CopyResult copyAndDigest(
            InputStream inputStream,
            Path temporaryFile,
            long expectedSize
    ) throws IOException {
        MessageDigest digest = newDigest();
        long actualSize = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream outputStream = new BufferedOutputStream(
                Files.newOutputStream(
                        temporaryFile,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING
                ),
                BUFFER_SIZE
        )) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                if (read > maxFileSize - actualSize) {
                    throw validationFailure(
                            FileStorageValidationException.Reason.TOO_LARGE,
                            "实际文件大小超过存储上限"
                    );
                }
                actualSize += read;
                if (actualSize > expectedSize) {
                    throw validationFailure(
                            FileStorageValidationException.Reason.SIZE_MISMATCH,
                            "实际文件大小与声明大小不一致"
                    );
                }
                digest.update(buffer, 0, read);
                outputStream.write(buffer, 0, read);
            }
        }
        if (actualSize != expectedSize) {
            throw validationFailure(
                    FileStorageValidationException.Reason.SIZE_MISMATCH,
                    "实际文件大小与声明大小不一致"
            );
        }
        return new CopyResult(
                actualSize,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前 JVM 不支持 SHA-256",
                    exception
            );
        }
    }

    private static FileStorageValidationException validationFailure(
            FileStorageValidationException.Reason reason,
            String message
    ) {
        return new FileStorageValidationException(reason, message);
    }

    private Path resolveStoredPath(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IOException("存储路径不能为空");
        }
        final Path relativePath;
        try {
            relativePath = Path.of(storagePath);
        } catch (InvalidPathException exception) {
            throw new IOException("存储路径格式不正确", exception);
        }
        if (relativePath.isAbsolute() || relativePath.getRoot() != null) {
            throw new IOException("存储路径必须是相对路径");
        }
        for (Path part : relativePath) {
            String value = part.toString();
            if (value.equals(".") || value.equals("..")) {
                throw new IOException("存储路径不能包含 . 或 ..");
            }
        }

        Path target = root.resolve(relativePath).normalize();
        requireInsideRoot(target);
        return target;
    }

    private void requireInsideRoot(Path path) throws IOException {
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IOException("存储路径超出根目录");
        }
    }

    private void requireSafeExistingParents(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null || !parent.startsWith(root)) {
            throw new IOException("存储路径超出根目录");
        }
        Path current = root;
        Path relativeParent = root.relativize(parent);
        for (Path part : relativeParent) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (Files.isSymbolicLink(current)) {
                throw new IOException("存储路径不能经过符号链接");
            }
        }
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(root)) {
            throw new IOException("存储路径经符号链接超出根目录");
        }
    }

    private void createSafeDirectories(Path directory) throws IOException {
        requireInsideRoot(directory);
        Path current = root;
        for (Path part : root.relativize(directory)) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    Files.createDirectory(current);
                } catch (FileAlreadyExistsException ignored) {
                    // 并发上传可能刚好创建了同一分层目录，下方统一重新校验。
                }
            }
            if (Files.isSymbolicLink(current)
                    || !Files.isDirectory(
                            current,
                            LinkOption.NOFOLLOW_LINKS
                    )) {
                throw new IOException(
                        "存储分层路径必须是非符号链接目录"
                );
            }
        }
        requireSafeExistingPath(directory, true);
    }

    private void requireSafeExistingPath(
            Path target,
            boolean directory
    ) throws IOException {
        requireInsideRoot(target);
        requireSafeExistingParents(target);
        if (Files.isSymbolicLink(target)) {
            throw new IOException("存储路径不能指向符号链接");
        }
        if (directory
                && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("存储分层路径不是目录");
        }
        Path realTarget = target.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realTarget.startsWith(root)) {
            throw new IOException("存储路径经符号链接超出根目录");
        }
    }

    private record CopyResult(long size, String sha256) {
    }
}
