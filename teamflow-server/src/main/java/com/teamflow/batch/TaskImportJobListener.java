package com.teamflow.batch;

import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.importjob.domain.ImportJob;
import com.teamflow.core.importjob.domain.ImportJobStatus;
import com.teamflow.core.importjob.mapper.ImportJobMapper;
import com.teamflow.metrics.TeamFlowMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.BatchStatus;
import org.springframework.transaction.annotation.Transactional;

/** 在批作业生命周期边界维护业务导入任务状态、统计和耗时指标。 */
public class TaskImportJobListener implements JobExecutionListener {

    private final ImportJobMapper importJobMapper;
    private final Clock clock;
    private final TeamFlowMetrics metrics;

    public TaskImportJobListener(
            ImportJobMapper importJobMapper,
            Clock clock,
            TeamFlowMetrics metrics
    ) {
        this.importJobMapper = Objects.requireNonNull(
                importJobMapper,
                "导入任务 Mapper 不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "监听器时钟不能为 null");
        this.metrics = Objects.requireNonNull(metrics, "业务指标不能为 null");
    }

    @Override
    @Transactional
    public void beforeJob(JobExecution jobExecution) {
        String importJobId = requireImportJobId(jobExecution);
        ImportJob importJob = requireImportJob(importJobId);
        if (importJob.getStatus() == ImportJobStatus.RUNNING) {
            return;
        }
        if (importJob.getStatus() != ImportJobStatus.PENDING) {
            throw new IllegalStateException(
                    "只有待执行或已由服务抢占的导入任务可以运行: "
                            + importJobId
            );
        }
        importJob.markRunning(UtcTimeText.now(clock));
        requireSingleUpdate(
                importJobMapper.update(
                        importJob,
                        ImportJobStatus.PENDING
                ),
                "标记导入任务为运行中"
        );
    }

    @Override
    @Transactional
    public void afterJob(JobExecution jobExecution) {
        String importJobId = requireImportJobId(jobExecution);
        ImportJob importJob = requireImportJob(importJobId);
        if (importJob.getStatus() != ImportJobStatus.RUNNING) {
            throw new IllegalStateException(
                    "批作业结束时业务导入任务不是运行中状态: "
                            + importJobId
            );
        }

        ImportStatistics statistics = statistics(jobExecution);
        boolean completed = jobExecution.getStatus() == BatchStatus.COMPLETED;
        String now = UtcTimeText.now(clock);
        if (completed) {
            importJob.markCompleted(
                    statistics.totalRows(),
                    statistics.successRows(),
                    statistics.failedRows(),
                    null,
                    now
            );
        } else {
            importJob.markFailed(
                    statistics.totalRows(),
                    statistics.successRows(),
                    statistics.failedRows(),
                    null,
                    now
            );
        }
        requireSingleUpdate(
                importJobMapper.update(
                        importJob,
                        ImportJobStatus.RUNNING
                ),
                "回写导入任务最终状态"
        );
        metrics.recordImportDuration(durationMillis(jobExecution), completed);
    }

    private ImportJob requireImportJob(String importJobId) {
        return importJobMapper.findById(importJobId)
                .orElseThrow(() -> new IllegalStateException(
                        "批处理对应的导入任务不存在: " + importJobId
                ));
    }

    private static String requireImportJobId(JobExecution jobExecution) {
        Objects.requireNonNull(jobExecution, "JobExecution 不能为 null");
        String importJobId = jobExecution.getJobParameters()
                .getString(TaskImportJobConfig.PARAM_IMPORT_JOB_ID);
        if (importJobId == null || importJobId.isBlank()) {
            throw new IllegalStateException(
                    "批作业参数 importJobId 不能为空"
            );
        }
        return importJobId;
    }

    private static ImportStatistics statistics(JobExecution execution) {
        long readRows = 0;
        long readSkips = 0;
        long successRows = 0;
        long explicitFailures = 0;
        for (StepExecution step : execution.getStepExecutions()) {
            readRows += step.getReadCount();
            readSkips += step.getReadSkipCount();
            successRows += step.getWriteCount();
            explicitFailures += step.getReadSkipCount()
                    + step.getProcessSkipCount()
                    + step.getWriteSkipCount();
        }
        long totalRows = readRows + readSkips;
        long failedRows = Math.max(
                explicitFailures,
                Math.max(0L, totalRows - successRows)
        );
        if (successRows + failedRows > totalRows) {
            failedRows = Math.max(0L, totalRows - successRows);
        }
        return new ImportStatistics(totalRows, successRows, failedRows);
    }

    private static long durationMillis(JobExecution execution) {
        LocalDateTime start = execution.getStartTime();
        LocalDateTime end = execution.getEndTime();
        if (start == null || end == null || end.isBefore(start)) {
            return 0L;
        }
        return Duration.between(start, end).toMillis();
    }

    private static void requireSingleUpdate(
            int affectedRows,
            String operation
    ) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    operation + "时受影响行数必须为 1"
            );
        }
    }

    private record ImportStatistics(
            long totalRows,
            long successRows,
            long failedRows
    ) {
    }
}
