package com.teamflow.core.task.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.error.ProjectErrorCode;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.task.error.TaskErrorCode;
import com.teamflow.core.task.mapper.TaskMapper;
import com.teamflow.core.task.service.TaskAccessContext;
import com.teamflow.core.task.service.TaskAccessService;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.service.TeamAuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 任务相关资源访问边界的默认实现。 */
@Service
public class TaskAccessServiceImpl implements TaskAccessService {

    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final TeamAuthorizationService authorizationService;

    public TaskAccessServiceImpl(
            TaskMapper taskMapper,
            ProjectMapper projectMapper,
            TeamAuthorizationService authorizationService
    ) {
        this.taskMapper = Objects.requireNonNull(
                taskMapper,
                "任务 Mapper 不能为 null"
        );
        this.projectMapper = Objects.requireNonNull(
                projectMapper,
                "项目 Mapper 不能为 null"
        );
        this.authorizationService = Objects.requireNonNull(
                authorizationService,
                "团队权限服务不能为 null"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Project requireProjectMember(
            String currentUserId,
            String projectId
    ) {
        Project project = requireProject(projectId);
        requireMember(project, currentUserId, false);
        return project;
    }

    @Override
    @Transactional
    public Project requireProjectMemberForUpdate(
            String currentUserId,
            String projectId,
            String relatedUserId
    ) {
        Project project = requireProjectForUpdate(projectId, false);
        requireMembersForUpdate(
                project,
                currentUserId,
                relatedUserId,
                false
        );
        return project;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskAccessContext requireTaskMember(
            String currentUserId,
            String taskId
    ) {
        return requireTaskAccess(currentUserId, taskId);
    }

    @Override
    @Transactional
    public TaskAccessContext requireTaskMemberForUpdate(
            String currentUserId,
            String taskId,
            String relatedUserId
    ) {
        Objects.requireNonNull(taskId, "任务编号不能为 null");
        Task visibleTask = taskMapper.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                        TaskErrorCode.TASK_NOT_FOUND
                ));
        Project project = requireProjectForUpdate(
                visibleTask.getProjectId(),
                true
        );
        Task lockedTask = taskMapper.findByIdForUpdate(taskId)
                .filter(task -> task.getProjectId().equals(project.getId()))
                .orElseThrow(() -> new BusinessException(
                        TaskErrorCode.TASK_NOT_FOUND
                ));
        requireMembersForUpdate(
                project,
                currentUserId,
                relatedUserId,
                true
        );
        return new TaskAccessContext(lockedTask, project);
    }

    private TaskAccessContext requireTaskAccess(
            String currentUserId,
            String taskId
    ) {
        Objects.requireNonNull(taskId, "任务编号不能为 null");
        Task task = taskMapper.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                        TaskErrorCode.TASK_NOT_FOUND
                ));
        Project project = projectMapper.findById(task.getProjectId())
                .orElseThrow(() -> new BusinessException(
                        TaskErrorCode.TASK_NOT_FOUND
                ));
        requireMember(project, currentUserId, true);
        return new TaskAccessContext(task, project);
    }

    private Project requireProject(String projectId) {
        Objects.requireNonNull(projectId, "项目编号不能为 null");
        return projectMapper.findById(projectId)
                .orElseThrow(() -> new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND
                ));
    }

    private Project requireProjectForUpdate(
            String projectId,
            boolean hideAsTask
    ) {
        Objects.requireNonNull(projectId, "项目编号不能为 null");
        return projectMapper.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BusinessException(
                        hideAsTask
                                ? TaskErrorCode.TASK_NOT_FOUND
                                : ProjectErrorCode.PROJECT_NOT_FOUND
                ));
    }

    private void requireMember(
            Project project,
            String currentUserId,
            boolean hideAsTask
    ) {
        Objects.requireNonNull(currentUserId, "当前用户编号不能为 null");
        try {
            authorizationService.requireMember(
                    project.getTeamId(),
                    currentUserId
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != TeamErrorCode.TEAM_NOT_FOUND) {
                throw exception;
            }
            throw new BusinessException(
                    hideAsTask
                            ? TaskErrorCode.TASK_NOT_FOUND
                            : ProjectErrorCode.PROJECT_NOT_FOUND
            );
        }
    }

    private void requireMembersForUpdate(
            Project project,
            String currentUserId,
            String relatedUserId,
            boolean hideAsTask
    ) {
        Objects.requireNonNull(currentUserId, "当前用户编号不能为 null");
        try {
            authorizationService.requireMembersForUpdate(
                    project.getTeamId(),
                    currentUserId,
                    relatedUserId
            );
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == TeamErrorCode.MEMBER_NOT_FOUND) {
                throw new BusinessException(
                        TaskErrorCode.ASSIGNEE_NOT_TEAM_MEMBER
                );
            }
            if (exception.getErrorCode() != TeamErrorCode.TEAM_NOT_FOUND) {
                throw exception;
            }
            throw new BusinessException(
                    hideAsTask
                            ? TaskErrorCode.TASK_NOT_FOUND
                            : ProjectErrorCode.PROJECT_NOT_FOUND
            );
        }
    }
}
