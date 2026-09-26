package com.teamflow.scheduling;

import com.teamflow.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 临时文件清理边界、深度和符号链接测试。 */
class TemporaryFileCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse(
            "2026-09-19T12:00:00Z"
    );

    @TempDir
    Path directory;

    @Test
    void shouldDeleteOnlyExpiredFilesInsideTemporaryRoot() throws IOException {
        Path storage = Files.createDirectories(directory.resolve("storage"));
        Path temporary = Files.createDirectories(storage.resolve("temp"));
        Path expired = Files.writeString(temporary.resolve("expired.tmp"), "x");
        Path fresh = Files.writeString(temporary.resolve("fresh.tmp"), "x");
        Path outside = Files.writeString(storage.resolve("keep.txt"), "x");
        makeOld(expired);
        makeOld(outside);
        Files.setLastModifiedTime(fresh, FileTime.from(NOW));

        scheduler(storage, 4).cleanExpiredTemporaryFiles();

        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(fresh));
        assertTrue(Files.exists(outside));
    }

    @Test
    void shouldRespectMaximumTraversalDepth() throws IOException {
        Path storage = Files.createDirectories(directory.resolve("storage"));
        Path temporary = Files.createDirectories(storage.resolve("temp"));
        Path direct = Files.writeString(temporary.resolve("direct.tmp"), "x");
        Path nestedDirectory = Files.createDirectories(temporary.resolve("nested"));
        Path nested = Files.writeString(nestedDirectory.resolve("nested.tmp"), "x");
        makeOld(direct);
        makeOld(nested);

        scheduler(storage, 1).cleanExpiredTemporaryFiles();

        assertFalse(Files.exists(direct));
        assertTrue(Files.exists(nested));
    }

    @Test
    void shouldNeverTraverseSymbolicTemporaryRoot() throws IOException {
        Path storage = Files.createDirectories(directory.resolve("storage"));
        Path outside = Files.createDirectories(directory.resolve("outside"));
        Path protectedFile = Files.writeString(outside.resolve("keep.tmp"), "x");
        makeOld(protectedFile);
        try {
            Files.createSymbolicLink(storage.resolve("temp"), outside);
        } catch (UnsupportedOperationException | IOException exception) {
            throw new TestAbortedException(
                    "当前文件系统不允许创建符号链接",
                    exception
            );
        }

        scheduler(storage, 4).cleanExpiredTemporaryFiles();

        assertTrue(Files.exists(protectedFile));
        assertTrue(Files.isSymbolicLink(storage.resolve("temp")));
    }

    private TemporaryFileCleanupScheduler scheduler(
            Path storage,
            int maxDepth
    ) {
        StorageProperties storageProperties = new StorageProperties(
                storage,
                DataSize.ofMegabytes(10)
        );
        OperationsProperties operations = new OperationsProperties(
                Duration.ofHours(24),
                100,
                Duration.ofMinutes(4),
                Duration.ofHours(1),
                maxDepth
        );
        return new TemporaryFileCleanupScheduler(
                storageProperties,
                operations,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void makeOld(Path path) throws IOException {
        Files.setLastModifiedTime(
                path,
                FileTime.from(NOW.minus(Duration.ofHours(2)))
        );
    }
}
