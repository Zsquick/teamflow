package com.teamflow.core.file.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.file.domain.Attachment;
import com.teamflow.core.file.dto.AttachmentResponse;
import com.teamflow.core.file.error.AttachmentErrorCode;
import com.teamflow.core.file.mapper.AttachmentMapper;
import com.teamflow.core.file.service.AttachmentDownload;
import com.teamflow.core.file.service.AttachmentService;
import com.teamflow.core.file.service.FileStorageService;
import com.teamflow.core.file.service.FileStorageValidationException;
import com.teamflow.core.file.service.StoredFile;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.service.TaskAccessContext;
import com.teamflow.core.task.service.TaskAccessService;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.service.TeamAuthorizationService;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 任务附件元数据、物理内容及跨资源补偿的默认实现。
 */
@Service
public class AttachmentServiceImpl implements AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(
            AttachmentServiceImpl.class
    );

    private final AttachmentMapper attachmentMapper;
    private final TaskAccessService taskAccessService;
    private final FileStorageService fileStorageService;
    private final TeamAuthorizationService authorizationService;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建附件业务实现。
     *
     * @param attachmentMapper 附件元数据访问接口
     * @param taskAccessService 任务访问边界
     * @param fileStorageService 文件内容存储端口
     * @param authorizationService 团队角色授权服务
     * @param idGenerator 可读资源编号生成器
     * @param clock 可测试时钟
     * @param transactionManager 事务管理器
     */
    public AttachmentServiceImpl(
            AttachmentMapper attachmentMapper,
            TaskAccessService taskAccessService,
            FileStorageService fileStorageService,
            TeamAuthorizationService authorizationService,
            ReadableIdGenerator idGenerator,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.attachmentMapper = Objects.requireNonNull(
                attachmentMapper,
                "附件 Mapper 不能为 null"
        );
        this.taskAccessService = Objects.requireNonNull(
                taskAccessService,
                "任务访问服务不能为 null"
        );
        this.fileStorageService = Objects.requireNonNull(
                fileStorageService,
                "文件存储服务不能为 null"
        );
        this.authorizationService = Objects.requireNonNull(
                authorizationService,
                "团队授权服务不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "附件时钟不能为 null");
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
    public AttachmentResponse upload(
            String currentUserId,
            String taskId,
            String originalName,
            String contentType,
            long size,
            InputStream inputStream
    ) throws IOException {
        Objects.requireNonNull(inputStream, "附件输入流不能为 null");
        if (size < 1L) {
            throw new BusinessException(
                    AttachmentErrorCode.ATTACHMENT_EMPTY
            );
        }

        final String safeOriginalName;
        final String safeContentType;
        try {
            safeOriginalName = Attachment.normalizeOriginalName(
                    originalName
            );
            safeContentType = Attachment.normalizeContentType(contentType);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    AttachmentErrorCode.ATTACHMENT_METADATA_INVALID
            );
        }

        TaskAccessContext preview = taskAccessService.requireTaskMember(
                currentUserId,
                taskId
        );
        requireActiveProject(preview.project());

        final StoredFile storedFile;
        try {
            storedFile = Objects.requireNonNull(
                    fileStorageService.store(
                            inputStream,
                            safeOriginalName,
                            size
                    ),
                    "文件存储结果不能为 null"
            );
        } catch (FileStorageValidationException exception) {
            throw new BusinessException(switch (exception.reason()) {
                case TOO_LARGE ->
                        AttachmentErrorCode.ATTACHMENT_TOO_LARGE;
                case SIZE_MISMATCH ->
                        AttachmentErrorCode.ATTACHMENT_SIZE_MISMATCH;
            });
        }
        try {
            AttachmentResponse response = transactionTemplate.execute(
                    status -> persistUploadedFile(
                            currentUserId,
                            taskId,
                            safeOriginalName,
                            safeContentType,
                            storedFile
                    )
            );
            if (response == null) {
                throw new IllegalStateException("附件元数据事务没有返回结果");
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
    public List<AttachmentResponse> listByTask(
            String currentUserId,
            String taskId
    ) {
        taskAccessService.requireTaskMember(currentUserId, taskId);
        return attachmentMapper.findByTaskId(taskId)
                .stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AttachmentDownload download(
            String currentUserId,
            String attachmentId
    ) {
        Attachment attachment = requireAttachment(attachmentId);
        requireReadableAttachmentTask(
                currentUserId,
                attachment.getTaskId()
        );
        String storagePath = attachment.getStoragePath();
        return new AttachmentDownload(
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSize(),
                () -> fileStorageService.open(storagePath)
        );
    }

    /** {@inheritDoc} */
    @Override
    public void delete(
            String currentUserId,
            String attachmentId
    ) {
        Attachment visibleAttachment = requireAttachment(attachmentId);
        Attachment deletedAttachment = transactionTemplate.execute(
                status -> deleteMetadata(
                        currentUserId,
                        visibleAttachment
                )
        );
        if (deletedAttachment == null) {
            throw new IllegalStateException("附件删除事务没有返回结果");
        }
        deletePhysicalFileAfterCommit(deletedAttachment);
    }

    private AttachmentResponse persistUploadedFile(
            String currentUserId,
            String taskId,
            String originalName,
            String contentType,
            StoredFile storedFile
    ) {
        TaskAccessContext access = taskAccessService
                .requireTaskMemberForUpdate(currentUserId, taskId);
        requireActiveProject(access.project());

        Attachment attachment = Attachment.create(
                idGenerator.nextId(ResourceType.ATTACHMENT),
                taskId,
                currentUserId,
                originalName,
                contentType,
                storedFile,
                UtcTimeText.now(clock)
        );
        requireSingleInsert(attachmentMapper.insert(attachment));
        return AttachmentResponse.from(attachment);
    }

    private Attachment deleteMetadata(
            String currentUserId,
            Attachment visibleAttachment
    ) {
        TaskAccessContext access = requireWritableAttachmentTask(
                currentUserId,
                visibleAttachment.getTaskId()
        );
        requireActiveProject(access.project());
        requireDeletePermissionBeforeAttachmentLock(
                currentUserId,
                visibleAttachment,
                access.project()
        );

        Attachment lockedAttachment = attachmentMapper
                .findByIdForUpdate(visibleAttachment.getId())
                .filter(attachment -> attachment.getTaskId().equals(
                        visibleAttachment.getTaskId()
                ))
                .filter(attachment -> attachment.getUploaderId().equals(
                        visibleAttachment.getUploaderId()
                ))
                .orElseThrow(AttachmentServiceImpl::attachmentNotFound);
        int affectedRows = attachmentMapper.deleteById(
                lockedAttachment.getId()
        );
        if (affectedRows == 1) {
            return lockedAttachment;
        }
        if (affectedRows != 0) {
            throw new IllegalStateException(
                    "删除附件元数据时受影响行数只能为 0 或 1"
            );
        }
        throw attachmentNotFound();
    }

    private void requireDeletePermissionBeforeAttachmentLock(
            String currentUserId,
            Attachment attachment,
            Project project
    ) {
        if (attachment.getUploaderId().equals(currentUserId)) {
            return;
        }
        try {
            authorizationService.requireAtLeast(
                    project.getTeamId(),
                    currentUserId,
                    TeamRole.ADMIN
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode()
                    == TeamErrorCode.INSUFFICIENT_PERMISSION) {
                throw new BusinessException(
                        AttachmentErrorCode.ATTACHMENT_DELETE_FORBIDDEN
                );
            }
            if (exception.getErrorCode() == TeamErrorCode.TEAM_NOT_FOUND) {
                throw attachmentNotFound();
            }
            throw exception;
        }
    }

    private TaskAccessContext requireWritableAttachmentTask(
            String currentUserId,
            String taskId
    ) {
        try {
            return taskAccessService.requireTaskMemberForUpdate(
                    currentUserId,
                    taskId
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == TaskErrorCode.TASK_NOT_FOUND) {
                throw attachmentNotFound();
            }
            throw exception;
        }
    }

    private void requireReadableAttachmentTask(
            String currentUserId,
            String taskId
    ) {
        try {
            taskAccessService.requireTaskMember(currentUserId, taskId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == TaskErrorCode.TASK_NOT_FOUND) {
                throw attachmentNotFound();
            }
            throw exception;
        }
    }

    private Attachment requireAttachment(String attachmentId) {
        Objects.requireNonNull(attachmentId, "附件编号不能为 null");
        return attachmentMapper.findById(attachmentId)
                .orElseThrow(AttachmentServiceImpl::attachmentNotFound);
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

    private void deletePhysicalFileAfterCommit(Attachment attachment) {
        try {
            fileStorageService.delete(attachment.getStoragePath());
        } catch (IOException | RuntimeException cleanupFailure) {
            log.warn(
                    "附件元数据已删除，物理文件等待后续清理 "
                            + "attachmentId={} cleanupFailureType={}",
                    attachment.getId(),
                    cleanupFailure.getClass().getName()
            );
        }
    }

    private static void requireActiveProject(Project project) {
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessException(
                    AttachmentErrorCode.PROJECT_NOT_ACTIVE
            );
        }
    }

    private static void requireSingleInsert(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增附件元数据时受影响行数必须为 1"
            );
        }
    }

    private static BusinessException attachmentNotFound() {
        return new BusinessException(
                AttachmentErrorCode.ATTACHMENT_NOT_FOUND
        );
    }
}
