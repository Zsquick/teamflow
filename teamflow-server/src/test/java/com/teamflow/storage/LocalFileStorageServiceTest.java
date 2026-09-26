package com.teamflow.storage;

import com.teamflow.core.file.service.StoredFile;
import com.teamflow.core.file.service.FileStorageValidationException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 本地 NIO 存储的流式复制、摘要和路径边界测试。 */
class LocalFileStorageServiceTest {

    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb924"
                    + "27ae41e4649b934ca495991b7852b855";
    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223"
                    + "b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldStoreOpenContentAndCalculateSha256() throws Exception {
        LocalFileStorageService storage = storage(1024);
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = storage.store(
                new ByteArrayInputStream(content),
                "report.txt",
                content.length
        );

        assertEquals(3, stored.size());
        assertEquals(ABC_SHA256, stored.sha256());
        assertFalse(Path.of(stored.storagePath()).isAbsolute());
        assertEquals(
                stored.storageName(),
                Path.of(stored.storagePath()).getFileName().toString()
        );
        assertTrue(storage.exists(stored.storagePath()));
        try (InputStream inputStream = storage.open(stored.storagePath())) {
            assertArrayEquals(content, inputStream.readAllBytes());
        }
    }

    @Test
    void shouldAllowEmptyAndCleanFailedSizeWrites() throws Exception {
        LocalFileStorageService storage = storage(4);
        StoredFile empty = storage.store(
                new ByteArrayInputStream(new byte[0]),
                "empty.txt",
                0
        );
        assertEquals(0, empty.size());
        assertEquals(EMPTY_SHA256, empty.sha256());

        assertThrows(IllegalArgumentException.class, () -> storage.store(
                new ByteArrayInputStream(new byte[0]),
                "negative.txt",
                -1
        ));
        assertThrows(FileStorageValidationException.class, () -> storage.store(
                new ByteArrayInputStream(new byte[5]),
                "declared-too-large.bin",
                5
        ));
        assertThrows(IOException.class, () -> storage.store(
                new ByteArrayInputStream(new byte[2]),
                "short.bin",
                3
        ));
        assertThrows(IOException.class, () -> storage.store(
                new ByteArrayInputStream(new byte[5]),
                "actual-too-large.bin",
                4
        ));

        Path root = temporaryDirectory.resolve("root");
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> regularFiles = paths
                    .filter(path -> Files.isRegularFile(path))
                    .toList();
            assertEquals(1, regularFiles.size());
            assertFalse(
                    regularFiles.getFirst().toString().endsWith(".part")
            );
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new StorageProperties(
                        Path.of(""),
                        DataSize.ofBytes(1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StorageProperties(
                        temporaryDirectory.resolve("invalid"),
                        DataSize.ofBytes(0)
                )
        );
    }

    @Test
    void shouldUseRandomPathsWithoutTrustingOriginalName() throws Exception {
        LocalFileStorageService storage = storage(1024);
        byte[] firstContent = "first".getBytes(StandardCharsets.UTF_8);
        byte[] secondContent = "second".getBytes(StandardCharsets.UTF_8);

        StoredFile first = storage.store(
                new ByteArrayInputStream(firstContent),
                "../same.txt",
                firstContent.length
        );
        StoredFile second = storage.store(
                new ByteArrayInputStream(secondContent),
                "../same.txt",
                secondContent.length
        );

        assertNotEquals(first.storageName(), second.storageName());
        assertNotEquals(first.storagePath(), second.storagePath());
        assertFalse(Files.exists(temporaryDirectory.resolve("same.txt")));
        try (InputStream inputStream = storage.open(first.storagePath())) {
            assertArrayEquals(firstContent, inputStream.readAllBytes());
        }
        try (InputStream inputStream = storage.open(second.storagePath())) {
            assertArrayEquals(secondContent, inputStream.readAllBytes());
        }
    }

    @Test
    void shouldRejectTraversalAndAbsolutePathsAndDeleteIdempotently()
            throws Exception {
        LocalFileStorageService storage = storage(1024);
        StoredFile stored = storage.store(
                new ByteArrayInputStream(new byte[]{1}),
                "one.bin",
                1
        );
        Path outside = temporaryDirectory.resolve("outside.bin");
        Files.write(outside, new byte[]{9});

        assertThrows(
                IOException.class,
                () -> storage.open("../outside.bin")
        );
        assertThrows(
                IOException.class,
                () -> storage.delete("../outside.bin")
        );
        assertThrows(
                IOException.class,
                () -> storage.open(outside.toAbsolutePath().toString())
        );
        assertFalse(storage.exists("../outside.bin"));
        assertTrue(Files.exists(outside));

        storage.delete(stored.storagePath());
        storage.delete(stored.storagePath());
        assertFalse(storage.exists(stored.storagePath()));
    }

    @Test
    void shouldRejectSymbolicLinkTargetsWhenSupported() throws Exception {
        LocalFileStorageService storage = storage(1024);
        Path root = temporaryDirectory.resolve("root");
        Path outside = temporaryDirectory.resolve("outside.txt");
        Path link = root.resolve("link.txt");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException
                 | SecurityException exception) {
            Assumptions.abort(
                    "当前文件系统不支持创建测试符号链接"
            );
        }

        assertThrows(IOException.class, () -> storage.open("link.txt"));
        assertThrows(IOException.class, () -> storage.delete("link.txt"));
        assertFalse(storage.exists("link.txt"));
        assertEquals("outside", Files.readString(outside));
    }

    private LocalFileStorageService storage(long maxBytes) {
        return new LocalFileStorageService(new StorageProperties(
                temporaryDirectory.resolve("root"),
                DataSize.ofBytes(maxBytes)
        ));
    }
}
