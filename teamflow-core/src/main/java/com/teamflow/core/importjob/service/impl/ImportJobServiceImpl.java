package com.teamflow.core.importjob.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.common.transaction.TransactionCallbacks;
import com.teamflow.core.file.service.FileStorageService;
import com.teamflow.core.file.service.FileStorageValidationException;
import com.teamflow.core.file.service.StoredFile;
import com.teamflow.core.importjob.domain.ImportJob;
import com.teamflow.core.importjob.domain.ImportJobStatus;
import com.teamflow.core.importjob.dto.ImportJobResponse;
import com.teamflow.core.importjob.error.ImportJobErrorCode;
import com.teamflow.core.importjob.mapper.ImportJobMapper;
import com.teamflow.core.importjob.service.ImportJobLauncher;
import com.teamflow.core.importjob.service.ImportJobService;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.error.ProjectErrorCode;
import com.teamflow.core.task.service.TaskAccessService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * CSV 文件保存、导入任务状态抢占和访问控制的默认实现。
 */
@Service
public class ImportJobServiceImpl implements ImportJobService {
    private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "application/octet-stream"
    );

    private final ImportJobMapper importJobMapper;
    private final TaskAccessService taskAccessService;
    private final FileStorageService fileStorageService;
    private final ImportJobLauncher importJobLauncher;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建导入任务业务实现。
     *
     * @param importJobMapper 导入任务数据访问接口
     * @param taskAccessService 项目成员访问边界
     * @param fileStorageService 文件存储端口
     * @param importJobLauncher 批任务启动端口
     * @param idGenerator 可读编号生成器
     * @param clock 项目统一时钟
     * @param transactionManager 事务管理器
     */
    public ImportJobServiceImpl(
            ImportJobMapper importJobMapper,
            TaskAccessService taskAccessService,
            FileStorageService fileStorageService,
            ImportJobLauncher importJobLauncher,
            ReadableIdGenerator idGenerator,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.importJobMapper = Objects.requireNonNull(
                importJobMapper,
                "导入任务 Mapper 不能为 null"
        );
        this.taskAccessService = Objects.requireNonNull(
                taskAccessService,
                "任务访问服务不能为 null"
        );
        this.fileStorageService = Objects.requireNonNull(
                fileStorageService,
                "文件存储服务不能为 null"
        );
        this.importJobLauncher = Objects.requireNonNull(
                importJobLauncher,
                "导入任务启动器不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "导入任务时钟不能为 null");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(
                        transactionManager,
                        "事务管理器不能为 null"
                )
        );
        this.transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    /** {@inheritDoc} */
    @Override
    public ImportJobResponse create(
            String currentUserId,
            String projectId,
            String originalName,
            String contentType,
            long size,
            InputStream inputStream
    ) throws IOException {
        Objects.requireNonNull(inputStream, "CSV 输入流不能为 null");
        if (size < 1L) {
            throw new BusinessException(ImportJobErrorCode.EMPTY_FILE);
        }
        String safeOriginalName = requireCsvFile(originalName, contentType);

        Project preview = taskAccessService.requireProjectMember(
                currentUserId,
                projectId
        );
        requireActiveProject(preview);

        final StoredFile storedFile;
        try {
            storedFile = Objects.requireNonNull(
                    fileStorageService.store(
                            inputStream,
                            safeOriginalName,
                            size
                    ),
                    "CSV 存储结果不能为 null"
            );
        } catch (FileStorageValidationException exception) {
            throw new BusinessException(switch (exception.reason()) {
                case TOO_LARGE -> ImportJobErrorCode.FILE_TOO_LARGE;
                case SIZE_MISMATCH -> ImportJobErrorCode.FILE_SIZE_MISMATCH;
            });
        }

        try {
            ImportJobResponse response = transactionTemplate.execute(
                    status -> persist(
                            currentUserId,
                            preview.getId(),
                            safeOriginalName,
                            storedFile
                    )
            );
            if (response == null) {
                throw new IllegalStateException("导入任务事务没有返回结果");
            }
            return response;
        } catch (RuntimeException | Error failure) {
            compensateStoredFile(storedFile.storagePath(), failure);
            throw failure;
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ImportJobResponse get(
            String currentUserId,
            String importJobId
    ) {
        ImportJob importJob = requireImportJob(importJobId);
        requireVisibleProject(currentUserId, importJob.getProjectId());
        return ImportJobResponse.from(importJob);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void start(String currentUserId, String importJobId) {
        ImportJob importJob = requireImportJob(importJobId);
        Project project = requireWritableProject(
                currentUserId,
                importJob.getProjectId()
        );
        requireActiveProject(project);
        if (importJob.getStatus() != ImportJobStatus.PENDING) {
            throw new BusinessException(ImportJobErrorCode.STATUS_CONFLICT);
        }

        importJob.markRunning(UtcTimeText.now(clock));
        int affectedRows = importJobMapper.update(
                importJob,
                ImportJobStatus.PENDING
        );
        if (affectedRows == 0) {
            throw new BusinessException(ImportJobErrorCode.STATUS_CONFLICT);
        }
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "抢占导入任务时受影响行数只能为 0 或 1"
            );
        }
        TransactionCallbacks.afterCommit(
                () -> importJobLauncher.launch(importJob.getId())
        );
    }

    private ImportJobResponse persist(
            String currentUserId,
            String projectId,
            String originalFileName,
            StoredFile storedFile
    ) {
        Project project = taskAccessService.requireProjectMemberForUpdate(
                currentUserId,
                projectId
        );
        requireActiveProject(project);
        ImportJob importJob = ImportJob.create(
                idGenerator.nextId(ResourceType.IMPORT_JOB),
                project.getId(),
                currentUserId,
                originalFileName,
                storedFile.storagePath(),
                UtcTimeText.now(clock)
        );
        int affectedRows = importJobMapper.insert(importJob);
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增导入任务时受影响行数必须为 1"
            );
        }
        return ImportJobResponse.from(importJob);
    }

    private ImportJob requireImportJob(String importJobId) {
        Objects.requireNonNull(importJobId, "导入任务编号不能为 null");
        return importJobMapper.findById(importJobId)
                .orElseThrow(ImportJobServiceImpl::notFound);
    }

    private Project requireVisibleProject(
            String currentUserId,
            String projectId
    ) {
        try {
            return taskAccessService.requireProjectMember(
                    currentUserId,
                    projectId
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ProjectErrorCode.PROJECT_NOT_FOUND) {
                throw notFound();
            }
            throw exception;
        }
    }

    private Project requireWritableProject(
            String currentUserId,
            String projectId
    ) {
        try {
            return taskAccessService.requireProjectMemberForUpdate(
                    currentUserId,
                    projectId
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ProjectErrorCode.PROJECT_NOT_FOUND) {
                throw notFound();
            }
            throw exception;
        }
    }

    private static void requireActiveProject(Project project) {
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessException(
                    ImportJobErrorCode.PROJECT_NOT_ACTIVE
            );
        }
    }

    private static String requireCsvFile(
            String originalName,
            String contentType
    ) {
        if (originalName == null) {
            throw new BusinessException(ImportJobErrorCode.INVALID_FILE_TYPE);
        }
        String normalized = originalName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String fileName = (separator < 0
                ? normalized
                : normalized.substring(separator + 1)).strip();
        if (fileName.isEmpty()
                || fileName.length() > 255
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")
                || fileName.codePoints().anyMatch(
                        codePoint -> codePoint < 0x20 || codePoint == 0x7f
                )) {
            throw new BusinessException(ImportJobErrorCode.INVALID_FILE_TYPE);
        }

        if (contentType != null && !contentType.isBlank()) {
            String mediaType = contentType.split(";", 2)[0]
                    .strip()
                    .toLowerCase(Locale.ROOT);
            if (!ACCEPTED_CONTENT_TYPES.contains(mediaType)) {
                throw new BusinessException(
                        ImportJobErrorCode.INVALID_FILE_TYPE
                );
            }
        }
        return fileName;
    }

    private void compensateStoredFile(
            String storagePath,
            Throwable originalFailure
    ) {
        try {
            fileStorageService.delete(storagePath);
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ImportJobErrorCode.IMPORT_JOB_NOT_FOUND);
    }
}
