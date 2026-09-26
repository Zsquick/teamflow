package com.teamflow.scheduling;

import com.teamflow.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

/** 只清理存储根目录下 temp 子目录中的过期普通文件。 */
@Component
public class TemporaryFileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            TemporaryFileCleanupScheduler.class
    );

    private final Path storageRoot;
    private final OperationsProperties operationsProperties;
    private final Clock clock;

    public TemporaryFileCleanupScheduler(
            StorageProperties storageProperties,
            OperationsProperties operationsProperties,
            Clock clock
    ) {
        this.storageRoot = Objects.requireNonNull(
                storageProperties,
                "存储配置不能为 null"
        ).root();
        this.operationsProperties = Objects.requireNonNull(
                operationsProperties,
                "运维配置不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "时钟不能为 null");
    }

    @Scheduled(
            fixedDelayString =
                    "${teamflow.operations.temp-cleanup-interval:PT1H}"
    )
    public void cleanExpiredTemporaryFiles() {
        Path temporaryRoot = storageRoot.resolve("temp").normalize();
        if (!temporaryRoot.startsWith(storageRoot)
                || temporaryRoot.equals(storageRoot)) {
            throw new IllegalStateException("临时文件目录必须位于存储根目录内");
        }
        if (!Files.exists(temporaryRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(temporaryRoot)
                || !Files.isDirectory(
                        temporaryRoot,
                        LinkOption.NOFOLLOW_LINKS
                )) {
            log.error("临时文件目录不是安全的普通目录 path={}", temporaryRoot);
            return;
        }

        final Path realTemporaryRoot;
        try {
            Path realStorageRoot = storageRoot.toRealPath();
            realTemporaryRoot = temporaryRoot.toRealPath();
            if (!realTemporaryRoot.startsWith(realStorageRoot)
                    || realTemporaryRoot.equals(realStorageRoot)) {
                log.error(
                        "临时文件真实目录越出存储根目录 path={}",
                        realTemporaryRoot
                );
                return;
            }
        } catch (IOException | RuntimeException exception) {
            log.error("解析临时文件真实目录失败 path={}", temporaryRoot, exception);
            return;
        }

        Instant threshold = clock.instant().minus(
                operationsProperties.temporaryFileRetention()
        );
        int[] deleted = {0};
        try (Stream<Path> candidates = Files.find(
                realTemporaryRoot,
                operationsProperties.temporaryFileMaxDepth(),
                (path, attributes) -> attributes.isRegularFile()
                        && attributes.lastModifiedTime()
                        .toInstant()
                        .isBefore(threshold)
        )) {
            candidates.forEach(path -> {
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(realTemporaryRoot)
                        || Files.isSymbolicLink(normalized)) {
                    log.warn("跳过不安全的临时文件路径 path={}", path);
                    return;
                }
                try {
                    Path realCandidate = normalized.toRealPath();
                    if (!realCandidate.startsWith(realTemporaryRoot)
                            || !Files.isRegularFile(
                                    realCandidate,
                                    LinkOption.NOFOLLOW_LINKS
                            )) {
                        log.warn("跳过越界或非普通临时文件 path={}", path);
                        return;
                    }
                    if (Files.deleteIfExists(normalized)) {
                        deleted[0]++;
                    }
                } catch (IOException | RuntimeException exception) {
                    log.warn("删除过期临时文件失败 path={}", normalized, exception);
                }
            });
            log.info("临时文件清理完成 deleted={}", deleted[0]);
        } catch (IOException | RuntimeException exception) {
            log.error(
                    "扫描临时文件目录失败 path={}",
                    realTemporaryRoot,
                    exception
            );
        }
    }
}
