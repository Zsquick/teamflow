package com.teamflow.core.dashboard.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.dashboard.dto.ProjectDashboardResponse;
import com.teamflow.core.dashboard.error.DashboardErrorCode;
import com.teamflow.core.dashboard.service.DashboardService;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.error.ProjectErrorCode;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.mapper.AssigneeCompletedCount;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.task.mapper.TaskStatusCount;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.service.TeamAuthorizationService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 使用专用有界线程池并行聚合项目统计。
 *
 * <p>项目可见性在提交异步查询前只校验一次。三项统计中任何一项
 * 超时、被拒绝或失败时，本次快照整体失败，避免把不完整数据伪装成真实的 0。</p>
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(
            DashboardServiceImpl.class
    );
    private static final long STATISTICS_TIMEOUT_MILLIS = 2_000L;

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final TeamAuthorizationService authorizationService;
    private final Executor dashboardExecutor;
    private final Clock clock;

    /**
     * 创建仪表盘业务实现。
     *
     * @param projectMapper 项目数据访问接口
     * @param taskMapper 任务数据访问接口
     * @param authorizationService 团队资源级权限服务
     * @param dashboardExecutor 仪表盘专用执行器
     * @param clock 可测试时钟
     */
    public DashboardServiceImpl(
            ProjectMapper projectMapper,
            TaskMapper taskMapper,
            TeamAuthorizationService authorizationService,
            @Qualifier("dashboardExecutor") Executor dashboardExecutor,
            Clock clock
    ) {
        this.projectMapper = Objects.requireNonNull(
                projectMapper,
                "项目 Mapper 不能为 null"
        );
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "任务 Mapper 不能为 null"
        );
        this.authorizationService = Objects.requireNonNull(
                authorizationService,
                "团队权限服务不能为 null"
        );
        this.dashboardExecutor = Objects.requireNonNull(
                dashboardExecutor,
                "仪表盘执行器不能为 null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "仪表盘时钟不能为 null"
        );
    }

    /** {@inheritDoc} */
    @Override
    public ProjectDashboardResponse getProjectDashboard(
            String currentUserId,
            String projectId
    ) {
        Objects.requireNonNull(currentUserId, "当前用户编号不能为 null");
        Objects.requireNonNull(projectId, "项目编号不能为 null");

        Project project = projectMapper.findById(projectId)
                .orElseThrow(() -> new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND
                ));
        requireProjectMember(currentUserId, project);

        String generatedAt = UtcTimeText.now(clock);
        List<CompletableFuture<?>> submitted = new ArrayList<>(3);
        CompletableFuture<List<TaskStatusCount>> statusFuture;
        CompletableFuture<Long> overdueFuture;
        CompletableFuture<List<AssigneeCompletedCount>> completedFuture;
        try {
            statusFuture = submitStatistics(
                    () -> taskMapper.countByStatus(projectId)
            );
            submitted.add(statusFuture);
            overdueFuture = submitStatistics(
                    () -> taskMapper.countOverdue(projectId, generatedAt)
            );
            submitted.add(overdueFuture);
            completedFuture = submitStatistics(
                    () -> taskMapper.countCompletedByAssignee(projectId)
            );
            submitted.add(completedFuture);
        } catch (RejectedExecutionException exception) {
            cancelAll(submitted);
            log.warn(
                    "仪表盘统计线程池已满, projectId={}",
                    projectId
            );
            throw new BusinessException(DashboardErrorCode.EXECUTOR_BUSY);
        }

        try {
            CompletableFuture.allOf(
                    statusFuture,
                    overdueFuture,
                    completedFuture
            ).join();

            Map<String, Long> statusCounts = toStatusCounts(
                    statusFuture.join()
            );
            Map<String, Long> memberCompletedCounts =
                    toMemberCompletedCounts(completedFuture.join());
            long totalTasks = statusCounts.values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum();
            return new ProjectDashboardResponse(
                    projectId,
                    totalTasks,
                    overdueFuture.join(),
                    statusCounts,
                    memberCompletedCounts,
                    generatedAt
            );
        } catch (CompletionException exception) {
            cancelAll(submitted);
            throw translateStatisticsFailure(projectId, exception);
        } catch (CancellationException exception) {
            cancelAll(submitted);
            log.error(
                    "仪表盘统计查询被取消, projectId={}",
                    projectId,
                    exception
            );
            throw new BusinessException(
                    DashboardErrorCode.STATISTICS_UNAVAILABLE
            );
        }
    }

    private void requireProjectMember(
            String currentUserId,
            Project project
    ) {
        try {
            authorizationService.requireMember(
                    project.getTeamId(),
                    currentUserId
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == TeamErrorCode.TEAM_NOT_FOUND) {
                throw new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND
                );
            }
            throw exception;
        }
    }

    private <T> CompletableFuture<T> submitStatistics(
            Supplier<T> query
    ) {
        return CompletableFuture
                .supplyAsync(query, dashboardExecutor)
                .orTimeout(
                        STATISTICS_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS
                );
    }

    private static Map<String, Long> toStatusCounts(
            List<TaskStatusCount> counts
    ) {
        Objects.requireNonNull(counts, "任务状态统计不能为 null");
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            result.put(status.name(), 0L);
        }
        for (TaskStatusCount count : counts) {
            Objects.requireNonNull(count, "任务状态统计项不能为 null");
            result.merge(
                    count.status().name(),
                    count.count(),
                    Math::addExact
            );
        }
        return result;
    }

    private static Map<String, Long> toMemberCompletedCounts(
            List<AssigneeCompletedCount> counts
    ) {
        Objects.requireNonNull(counts, "成员完成统计不能为 null");
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (AssigneeCompletedCount count : counts) {
            Objects.requireNonNull(count, "成员完成统计项不能为 null");
            result.merge(
                    count.assigneeId(),
                    count.count(),
                    Math::addExact
            );
        }
        return result;
    }

    private static void cancelAll(
            List<CompletableFuture<?>> futures
    ) {
        futures.forEach(future -> future.cancel(true));
    }

    private static RuntimeException translateStatisticsFailure(
            String projectId,
            CompletionException exception
    ) {
        Throwable cause = unwrapCompletionFailure(exception);
        if (cause instanceof BusinessException businessException) {
            return businessException;
        }
        if (cause instanceof TimeoutException) {
            log.warn(
                    "仪表盘统计查询超时, projectId={}",
                    projectId
            );
            return new BusinessException(
                    DashboardErrorCode.STATISTICS_TIMEOUT
            );
        }

        log.error(
                "仪表盘统计查询失败, projectId={}",
                projectId,
                cause
        );
        return new BusinessException(
                DashboardErrorCode.STATISTICS_UNAVAILABLE
        );
    }

    private static Throwable unwrapCompletionFailure(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
