package com.teamflow.core.task.service.impl;

import com.teamflow.common.api.PageQuery;
import com.teamflow.common.api.PageResult;
import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.domain.TaskStatus;
import com.teamflow.core.task.dto.ChangeTaskStatusRequest;
import com.teamflow.core.task.dto.CreateTaskRequest;
import com.teamflow.core.task.dto.TaskResponse;
import com.teamflow.core.task.dto.UpdateTaskRequest;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.event.TaskAssignmentChanged;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.task.service.TaskAccessContext;
import com.teamflow.core.task.service.TaskAccessService;
import com.teamflow.core.task.service.TaskEventPublisher;
import com.teamflow.core.task.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** 任务创建、看板查询、乐观锁修改和软删除的默认实现。 */
@Service
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final TaskAccessService taskAccessService;
    private final ReadableIdGenerator idGenerator;
    private final TaskEventPublisher taskEventPublisher;
    private final Clock clock;

    public TaskServiceImpl(
            TaskMapper taskMapper,
            TaskAccessService taskAccessService,
            ReadableIdGenerator idGenerator,
            TaskEventPublisher taskEventPublisher,
            Clock clock
    ) {
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "任务 Mapper 不能为 null"
        );
        this.taskAccessService = Objects.requireNonNull(
                taskAccessService,
                "任务访问服务不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.taskEventPublisher = Objects.requireNonNull(
                taskEventPublisher,
                "任务事件发布器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "任务时钟不能为 null");
    }

    @Override
    @Transactional
    public TaskResponse create(
            String currentUserId,
            CreateTaskRequest request
    ) {
        Objects.requireNonNull(request, "创建任务请求不能为 null");
        Project project = taskAccessService.requireProjectMemberForUpdate(
                currentUserId,
                request.projectId(),
                request.assigneeId()
        );
        requireActiveProject(project);

        String now = UtcTimeText.now(clock);
        Task task = Task.create(
                idGenerator.nextId(ResourceType.TASK),
                project.getId(),
                request.title(),
                request.description(),
                request.priority(),
                request.assigneeId(),
                currentUserId,
                request.dueAt(),
                now
        );
        requireSingleInsert(taskMapper.insert(task));
        if (task.getAssigneeId() != null) {
            recordAssignmentChanged(
                    assignmentEvent(task, null, currentUserId)
            );
        }
        return TaskResponse.from(task);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TaskResponse> listByProject(
            String currentUserId,
            String projectId,
            TaskStatus status,
            PageQuery pageQuery
    ) {
        Objects.requireNonNull(pageQuery, "分页请求不能为 null");
        Project project = taskAccessService.requireProjectMember(
                currentUserId,
                projectId
        );
        List<TaskResponse> items = taskMapper.findByProjectId(
                        project.getId(),
                        status,
                        pageQuery.size(),
                        pageQuery.offset()
                )
                .stream()
                .map(TaskResponse::from)
                .toList();
        long total = taskMapper.countByProjectId(project.getId(), status);
        return PageResult.of(items, pageQuery, total);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse get(String currentUserId, String taskId) {
        return TaskResponse.from(
                taskAccessService.requireTaskMember(
                        currentUserId,
                        taskId
                ).task()
        );
    }

    @Override
    @Transactional
    public TaskResponse update(
            String currentUserId,
            String taskId,
            UpdateTaskRequest request
    ) {
        Objects.requireNonNull(request, "修改任务请求不能为 null");
        TaskAccessContext access = taskAccessService
                .requireTaskMemberForUpdate(
                        currentUserId,
                        taskId,
                        request.assigneeId()
                );
        Task task = access.task();
        Project project = access.project();
        requireActiveProject(project);
        requireExpectedVersion(
                task,
                Objects.requireNonNull(
                        request.version(),
                        "任务版本不能为 null"
                )
        );
        if (!task.getStatus().canTransitionTo(request.status())) {
            throw new BusinessException(
                    TaskErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        int expectedVersion = task.getVersion();
        String previousAssigneeId = task.getAssigneeId();
        boolean changed = task.updateDetails(
                request.title(),
                request.description(),
                request.status(),
                request.priority(),
                request.assigneeId(),
                request.dueAt(),
                UtcTimeText.now(clock)
        );
        if (!changed) {
            return TaskResponse.from(task);
        }

        requireSuccessfulChange(
                taskMapper.update(task, expectedVersion),
                "修改任务"
        );
        Task saved = taskMapper.findById(taskId)
                .orElseThrow(() -> new IllegalStateException(
                        "任务更新成功后无法重新读取: " + taskId
                ));
        if (!Objects.equals(previousAssigneeId, saved.getAssigneeId())) {
            recordAssignmentChanged(
                    assignmentEvent(
                            saved,
                            previousAssigneeId,
                            currentUserId
                    )
            );
        }
        return TaskResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(
            String currentUserId,
            String taskId,
            int expectedVersion
    ) {
        TaskAccessContext access = taskAccessService
                .requireTaskMemberForUpdate(currentUserId, taskId);
        requireActiveProject(access.project());
        requireExpectedVersion(access.task(), expectedVersion);
        requireSuccessfulChange(
                taskMapper.softDelete(
                        taskId,
                        expectedVersion,
                        UtcTimeText.now(clock)
                ),
                "删除任务"
        );
    }

    @Override
    @Transactional
    public TaskResponse changeStatus(
            String currentUserId,
            String taskId,
            ChangeTaskStatusRequest request
    ) {
        Objects.requireNonNull(request, "任务状态变更请求不能为 null");
        TaskAccessContext access = taskAccessService
                .requireTaskMemberForUpdate(currentUserId, taskId);
        Task task = access.task();
        requireActiveProject(access.project());
        int expectedVersion = Objects.requireNonNull(
                request.version(),
                "任务版本不能为 null"
        );
        requireExpectedVersion(task, expectedVersion);
        if (!task.getStatus().canTransitionTo(request.status())) {
            throw new BusinessException(
                    TaskErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        boolean changed = task.updateDetails(
                task.getTitle(),
                task.getDescription(),
                request.status(),
                task.getPriority(),
                task.getAssigneeId(),
                task.getDueAt(),
                UtcTimeText.now(clock)
        );
        if (!changed) {
            return TaskResponse.from(task);
        }
        requireSuccessfulChange(
                taskMapper.update(task, expectedVersion),
                "变更任务状态"
        );
        return TaskResponse.from(
                taskMapper.findById(taskId)
                        .orElseThrow(() -> new IllegalStateException(
                                "任务状态更新成功后无法重新读取: " + taskId
                        ))
        );
    }

    private static void requireActiveProject(Project project) {
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessException(TaskErrorCode.PROJECT_NOT_ACTIVE);
        }
    }

    private static void requireExpectedVersion(
            Task task,
            int expectedVersion
    ) {
        if (expectedVersion < 0 || task.getVersion() != expectedVersion) {
            throw new BusinessException(
                    TaskErrorCode.TASK_VERSION_CONFLICT
            );
        }
    }

    private static void requireSingleInsert(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增任务时受影响行数必须为 1"
            );
        }
    }

    private static void requireSuccessfulChange(
            int affectedRows,
            String operation
    ) {
        if (affectedRows == 1) {
            return;
        }
        if (affectedRows != 0) {
            throw new IllegalStateException(
                    operation + "时受影响行数只能为 0 或 1"
            );
        }
        throw new BusinessException(TaskErrorCode.TASK_VERSION_CONFLICT);
    }

    private static TaskAssignmentChanged assignmentEvent(
            Task task,
            String previousAssigneeId,
            String operatorId
    ) {
        return new TaskAssignmentChanged(
                task.getId(),
                task.getProjectId(),
                task.getVersion(),
                previousAssigneeId,
                task.getAssigneeId(),
                operatorId,
                task.getUpdatedAt()
        );
    }

    private void recordAssignmentChanged(
            TaskAssignmentChanged event
    ) {
        taskEventPublisher.publishAssignmentChanged(event);
    }
}
