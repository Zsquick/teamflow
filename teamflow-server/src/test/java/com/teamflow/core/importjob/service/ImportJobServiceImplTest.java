package com.teamflow.core.importjob.service;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.file.service.FileStorageService;
import com.teamflow.core.file.service.StoredFile;
import com.teamflow.core.importjob.domain.ImportJob;
import com.teamflow.core.importjob.domain.ImportJobStatus;
import com.teamflow.core.importjob.dto.ImportJobResponse;
import com.teamflow.core.importjob.error.ImportJobErrorCode;
import com.teamflow.core.importjob.mapper.ImportJobMapper;
import com.teamflow.core.importjob.service.impl.ImportJobServiceImpl;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.task.service.TaskAccessService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportJobServiceImplTest {

    private static final String NOW = "2026-09-19T08:00:00.000Z";
    private static final String USER_ID = "u001";
    private static final String PROJECT_ID = "p001";
    private static final String IMPORT_JOB_ID = "ij001";
    private static final StoredFile STORED_FILE = new StoredFile(
            "stored.csv",
            "imports/stored.csv",
            10L,
            "a".repeat(64)
    );

    @Mock
    private ImportJobMapper importJobMapper;
    @Mock
    private TaskAccessService taskAccessService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ImportJobLauncher importJobLauncher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ImportJobServiceImpl service;
    private Project project;

    @BeforeEach
    void setUp() {
        ReadableIdGenerator idGenerator = resourceType -> IMPORT_JOB_ID;
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-19T08:00:00Z"),
                ZoneOffset.UTC
        );
        service = new ImportJobServiceImpl(
                importJobMapper,
                taskAccessService,
                fileStorageService,
                importJobLauncher,
                idGenerator,
                clock,
                transactionManager
        );
        project = Project.create(
                PROJECT_ID,
                "tm001",
                "Import Project",
                "IMPORT",
                null,
                USER_ID,
                NOW
        );
    }

    @Test
    void shouldPersistStoredCsvInsideTransaction() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        when(taskAccessService.requireProjectMember(USER_ID, PROJECT_ID))
                .thenReturn(project);
        when(fileStorageService.store(any(), eq("tasks.csv"), eq(10L)))
                .thenReturn(STORED_FILE);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID,
                PROJECT_ID
        )).thenReturn(project);
        when(importJobMapper.insert(any(ImportJob.class))).thenReturn(1);

        ImportJobResponse response = service.create(
                USER_ID,
                PROJECT_ID,
                "tasks.csv",
                "text/csv; charset=UTF-8",
                content.length,
                new ByteArrayInputStream(content)
        );

        assertEquals(IMPORT_JOB_ID, response.id());
        assertEquals(ImportJobStatus.PENDING, response.status());
        verify(transactionManager).commit(any(TransactionStatus.class));
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void shouldDeleteStoredFileWhenDatabaseWorkFails() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        when(taskAccessService.requireProjectMember(USER_ID, PROJECT_ID))
                .thenReturn(project);
        when(fileStorageService.store(any(), eq("tasks.csv"), eq(10L)))
                .thenReturn(STORED_FILE);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID,
                PROJECT_ID
        )).thenReturn(project);
        when(importJobMapper.insert(any(ImportJob.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> service.create(
                        USER_ID,
                        PROJECT_ID,
                        "tasks.csv",
                        "text/csv",
                        content.length,
                        new ByteArrayInputStream(content)
                )
        );

        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(fileStorageService).delete(STORED_FILE.storagePath());
    }

    @Test
    void shouldClaimPendingJobOnceAndLaunchAfterSuccessfulUpdate() {
        ImportJob importJob = pendingJob();
        when(importJobMapper.findById(IMPORT_JOB_ID))
                .thenReturn(Optional.of(importJob));
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID,
                PROJECT_ID
        )).thenReturn(project);
        when(importJobMapper.update(
                importJob,
                ImportJobStatus.PENDING
        )).thenReturn(1);

        service.start(USER_ID, IMPORT_JOB_ID);

        assertEquals(ImportJobStatus.RUNNING, importJob.getStatus());
        verify(importJobLauncher).launch(IMPORT_JOB_ID);
    }

    @Test
    void shouldRejectConcurrentStartWithoutLaunchingAgain() {
        ImportJob importJob = pendingJob();
        when(importJobMapper.findById(IMPORT_JOB_ID))
                .thenReturn(Optional.of(importJob));
        when(taskAccessService.requireProjectMemberForUpdate(
                USER_ID,
                PROJECT_ID
        )).thenReturn(project);
        when(importJobMapper.update(
                importJob,
                ImportJobStatus.PENDING
        )).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.start(USER_ID, IMPORT_JOB_ID)
        );

        assertEquals(ImportJobErrorCode.STATUS_CONFLICT,
                exception.getErrorCode());
        verify(importJobLauncher, never()).launch(any());
    }

    private static ImportJob pendingJob() {
        return ImportJob.create(
                IMPORT_JOB_ID,
                PROJECT_ID,
                USER_ID,
                "tasks.csv",
                "imports/stored.csv",
                NOW
        );
    }
}
