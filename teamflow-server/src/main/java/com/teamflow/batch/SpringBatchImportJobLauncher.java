package com.teamflow.batch;

import com.teamflow.concurrent.CancellationAwareRunnable;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.importjob.domain.ImportJob;
import com.teamflow.core.importjob.domain.ImportJobStatus;
import com.teamflow.core.importjob.mapper.ImportJobMapper;
import com.teamflow.core.importjob.service.ImportJobLauncher;
import com.teamflow.config.AsyncExecutorConfig;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用独立有界线程池异步调用 Spring Batch JobLauncher。 */
@Component
public class SpringBatchImportJobLauncher implements ImportJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(
            SpringBatchImportJobLauncher.class
    );

    private final JobLauncher jobLauncher;
    private final Job taskCsvImportJob;
    private final ImportJobMapper importJobMapper;
    private final Executor executor;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public SpringBatchImportJobLauncher(
            JobLauncher jobLauncher,
            @Qualifier(TaskImportJobConfig.JOB_NAME) Job taskCsvImportJob,
            ImportJobMapper importJobMapper,
            @Qualifier(AsyncExecutorConfig.BATCH_LAUNCHER_EXECUTOR)
            Executor executor,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.jobLauncher = Objects.requireNonNull(
                jobLauncher,
                "JobLauncher 不能为 null"
        );
        this.taskCsvImportJob = Objects.requireNonNull(
                taskCsvImportJob,
                "CSV 导入 Job 不能为 null"
        );
        this.importJobMapper = Objects.requireNonNull(
                importJobMapper,
                "导入任务 Mapper 不能为 null"
        );
        this.executor = Objects.requireNonNull(
                executor,
                "批处理启动执行器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "启动器时钟不能为 null");
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

    @Override
    public void launch(String importJobId) {
        Objects.requireNonNull(importJobId, "导入任务编号不能为 null");
        ImportJob importJob = importJobMapper.findById(importJobId)
                .orElseThrow(() -> new IllegalStateException(
                        "待启动导入任务不存在: " + importJobId
                ));
        JobParameters parameters = parameters(importJob);
        try {
            executor.execute(new BatchLaunchTask(importJobId, parameters));
        } catch (RuntimeException exception) {
            markLaunchFailed(importJobId, exception);
        }
    }

    private void run(String importJobId, JobParameters parameters) {
        try {
            jobLauncher.run(taskCsvImportJob, parameters);
        } catch (Exception exception) {
            markLaunchFailed(importJobId, exception);
        }
    }

    private JobParameters parameters(ImportJob importJob) {
        if (importJob.getStatus() != ImportJobStatus.RUNNING) {
            throw new IllegalStateException(
                    "只有运行中导入任务可以交给 Batch: " + importJob.getId()
            );
        }
        return new JobParametersBuilder()
                .addString(
                        TaskImportJobConfig.PARAM_IMPORT_JOB_ID,
                        importJob.getId()
                )
                .addString(
                        TaskImportJobConfig.PARAM_PROJECT_ID,
                        importJob.getProjectId()
                )
                .addString(
                        TaskImportJobConfig.PARAM_REPORTER_ID,
                        importJob.getCreatorId()
                )
                .addString(
                        TaskImportJobConfig.PARAM_STORAGE_PATH,
                        importJob.getStoragePath()
                )
                .addLong(
                        TaskImportJobConfig.PARAM_REQUEST_TIME,
                        clock.millis()
                )
                .toJobParameters();
    }

    private void markLaunchFailed(
            String importJobId,
            Throwable failure
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ImportJob current = importJobMapper.findById(importJobId)
                        .orElse(null);
                if (current == null
                        || current.getStatus() != ImportJobStatus.RUNNING) {
                    return;
                }
                current.markFailed(
                        current.getTotalRows(),
                        current.getSuccessRows(),
                        current.getFailedRows(),
                        current.getErrorFilePath(),
                        UtcTimeText.now(clock)
                );
                int affectedRows = importJobMapper.update(
                        current,
                        ImportJobStatus.RUNNING
                );
                if (affectedRows != 1) {
                    throw new IllegalStateException(
                            "启动失败回写时受影响行数必须为 1"
                    );
                }
            });
        } catch (RuntimeException updateFailure) {
            failure.addSuppressed(updateFailure);
        }
        log.error(
                "CSV 批作业启动失败 importJobId={} failureType={}",
                importJobId,
                failure.getClass().getName(),
                failure
        );
    }

    private final class BatchLaunchTask
            implements CancellationAwareRunnable {

        private final String importJobId;
        private final JobParameters parameters;

        private BatchLaunchTask(
                String importJobId,
                JobParameters parameters
        ) {
            this.importJobId = importJobId;
            this.parameters = parameters;
        }

        @Override
        public void run() {
            SpringBatchImportJobLauncher.this.run(
                    importJobId,
                    parameters
            );
        }

        @Override
        public void cancelled() {
            markLaunchFailed(
                    importJobId,
                    new IllegalStateException("应用关闭，批作业未启动")
            );
        }
    }
}
