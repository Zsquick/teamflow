package com.teamflow.batch;

import com.teamflow.concurrent.CancellationAwareRunnable;
import com.teamflow.core.importjob.domain.ImportJob;
import com.teamflow.core.importjob.domain.ImportJobStatus;
import com.teamflow.core.importjob.mapper.ImportJobMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringBatchImportJobLauncherTest {

    private static final String NOW = "2026-09-19T08:00:00.000Z";
    private static final String IMPORT_JOB_ID = "ij001";

    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private Job job;
    @Mock
    private ImportJobMapper importJobMapper;
    @Mock
    private Executor executor;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SpringBatchImportJobLauncher launcher;
    private ImportJob importJob;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-19T08:01:00Z"),
                ZoneOffset.UTC
        );
        launcher = new SpringBatchImportJobLauncher(
                jobLauncher,
                job,
                importJobMapper,
                executor,
                clock,
                transactionManager
        );
        importJob = ImportJob.create(
                IMPORT_JOB_ID,
                "p001",
                "u001",
                "tasks.csv",
                "imports/tasks.csv",
                NOW
        );
        importJob.markRunning(NOW);
        when(importJobMapper.findById(IMPORT_JOB_ID))
                .thenReturn(Optional.of(importJob));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(importJobMapper.update(importJob, ImportJobStatus.RUNNING))
                .thenReturn(1);
    }

    @Test
    void shouldMarkJobFailedWhenBoundedExecutorRejectsLaunch() {
        doThrow(new RejectedExecutionException("queue full"))
                .when(executor)
                .execute(any(Runnable.class));

        launcher.launch(IMPORT_JOB_ID);

        assertEquals(ImportJobStatus.FAILED, importJob.getStatus());
        verify(importJobMapper).update(importJob, ImportJobStatus.RUNNING);
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    void shouldMarkJobFailedWhenSpringBatchCannotStart() throws Exception {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(jobLauncher.run(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException(
                        "already running"
                ));

        launcher.launch(IMPORT_JOB_ID);

        assertEquals(ImportJobStatus.FAILED, importJob.getStatus());
        verify(importJobMapper).update(importJob, ImportJobStatus.RUNNING);
    }

    @Test
    void shouldMarkQueuedJobFailedWhenShutdownCancelsLaunch()
            throws Exception {
        AtomicReference<CancellationAwareRunnable> submitted =
                new AtomicReference<>();
        doAnswer(invocation -> {
            submitted.set(assertInstanceOf(
                    CancellationAwareRunnable.class,
                    invocation.<Runnable>getArgument(0)
            ));
            return null;
        }).when(executor).execute(any(Runnable.class));

        launcher.launch(IMPORT_JOB_ID);

        assertEquals(ImportJobStatus.RUNNING, importJob.getStatus());
        CancellationAwareRunnable queuedTask = submitted.get();
        assertNotNull(queuedTask);
        queuedTask.cancelled();

        assertEquals(ImportJobStatus.FAILED, importJob.getStatus());
        verify(importJobMapper).update(importJob, ImportJobStatus.RUNNING);
        verify(transactionManager).commit(any(TransactionStatus.class));
        verify(jobLauncher, never()).run(
                any(Job.class),
                any(JobParameters.class)
        );
    }
}
