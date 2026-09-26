package com.teamflow.batch;

import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.file.service.FileStorageService;
import com.teamflow.core.importjob.mapper.ImportJobMapper;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.user.mapper.UserMapper;
import com.teamflow.metrics.TeamFlowMetrics;
import java.time.Clock;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch CSV 导入作业、步骤及步骤作用域组件定义。
 */
@Configuration(proxyBeanMethods = false)
public class TaskImportJobConfig {

    public static final String JOB_NAME = "taskCsvImportJob";
    public static final String STEP_NAME = "taskCsvImportStep";
    public static final String PARAM_IMPORT_JOB_ID = "importJobId";
    public static final String PARAM_PROJECT_ID = "projectId";
    public static final String PARAM_REPORTER_ID = "reporterId";
    public static final String PARAM_STORAGE_PATH = "storagePath";
    public static final String PARAM_REQUEST_TIME = "requestTime";

    static final int CHUNK_SIZE = 100;
    static final int SKIP_LIMIT = 20;

    @Bean
    @StepScope
    public ItemStreamReader<TaskCsvRow> taskCsvReader(
            TaskCsvReaderFactory readerFactory,
            @Value("#{jobParameters['storagePath']}") String storagePath
    ) {
        return readerFactory.create(storagePath);
    }

    @Bean
    @StepScope
    public TaskCsvProcessor taskCsvProcessor(
            UserMapper userMapper,
            TeamMemberMapper teamMemberMapper,
            ProjectMapper projectMapper,
            ReadableIdGenerator idGenerator,
            Clock clock,
            @Value("#{jobParameters['projectId']}") String projectId,
            @Value("#{jobParameters['reporterId']}") String reporterId
    ) {
        return new TaskCsvProcessor(
                userMapper,
                teamMemberMapper,
                projectMapper,
                idGenerator,
                clock,
                projectId,
                reporterId
        );
    }

    @Bean
    public TaskCsvWriter taskCsvWriter(TaskMapper taskMapper) {
        return new TaskCsvWriter(taskMapper);
    }

    @Bean
    public TaskImportJobListener taskImportJobListener(
            ImportJobMapper importJobMapper,
            Clock clock,
            TeamFlowMetrics metrics
    ) {
        return new TaskImportJobListener(importJobMapper, clock, metrics);
    }

    @Bean(name = STEP_NAME)
    public Step taskCsvImportStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  @Qualifier("taskCsvReader") ItemStreamReader<TaskCsvRow> reader,
                                  ItemProcessor<TaskCsvRow, Task> processor,
                                  ItemWriter<Task> writer) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<TaskCsvRow, Task>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(CsvRowValidationException.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    @Bean(name = JOB_NAME)
    public Job taskCsvImportJob(JobRepository jobRepository,
                                @Qualifier(STEP_NAME) Step taskCsvImportStep,
                                @Qualifier("taskImportJobListener")
                                JobExecutionListener listener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(listener)
                .start(taskCsvImportStep)
                .build();
    }
}
