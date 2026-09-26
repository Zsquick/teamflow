package com.teamflow.core.project.service.impl;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.dto.CreateProjectRequest;
import com.teamflow.core.project.dto.ProjectResponse;
import com.teamflow.core.project.dto.UpdateProjectRequest;
import com.teamflow.core.project.error.ProjectErrorCode;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.project.service.ProjectService;
import com.teamflow.core.team.domain.TeamRole;
import com.teamflow.core.team.error.TeamErrorCode;
import com.teamflow.core.team.service.TeamAuthorizationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

/**
 * 项目创建、查询和乐观锁修改的默认业务实现。
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final TeamAuthorizationService authorizationService;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;


    /**
     * 创建项目业务实现。
     *
     * @param projectMapper 项目数据访问接口
     * @param authorizationService 团队资源级权限服务
     * @param idGenerator 可读编号生成器
     * @param clock 可测试时钟
     */
    public ProjectServiceImpl(
            ProjectMapper projectMapper,
            TeamAuthorizationService authorizationService,
            ReadableIdGenerator idGenerator,
            Clock clock
    ) {
        this.projectMapper = Objects.requireNonNull(
                projectMapper,
                "项目 Mapper 不能为 null"
        );
        this.authorizationService = Objects.requireNonNull(
                authorizationService,
                "团队权限服务不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "项目时钟不能为 null");
    }

    private Project requireProject(String projectId) {
        Objects.requireNonNull(projectId, "项目编号不能为 null");
        return projectMapper.findById(projectId)
                .orElseThrow(() -> new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND
                ));

    }

    private Project requireProjectForUpdate(String projectId) {
        Objects.requireNonNull(projectId, "项目编号不能为 null");
        return projectMapper.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND
                ));
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
            throw hideMissingTeamMembership(exception);
        }
    }

    private void requireProjectAdministrator(
            String currentUserId,
            Project project
    ) {
        try {
            authorizationService.requireAtLeast(
                    project.getTeamId(),
                    currentUserId,
                    TeamRole.ADMIN
            );
        } catch (BusinessException exception) {
            throw hideMissingTeamMembership(exception);
        }
    }

    private static BusinessException hideMissingTeamMembership(
            BusinessException exception
    ) {
        if (exception.getErrorCode() == TeamErrorCode.TEAM_NOT_FOUND) {
            return new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        return exception;
    }

    private static void requireSingleInsert(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException(
                    "新增项目时受影响行数必须为 1"
            );
        }
    }

    private static void requireSuccessfulUpdate(int affectedRows) {
        if (affectedRows == 1) {
            return;
        }
        if (affectedRows != 0) {
            throw new IllegalStateException(
                    "修改项目时受影响行数只能为 0 或 1"
            );
        }
        throw new BusinessException(
                ProjectErrorCode.PROJECT_VERSION_CONFLICT
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProjectResponse create(
            String currentUserId,
            CreateProjectRequest request
    ) {
        Objects.requireNonNull(currentUserId, "当前用户编号不能为 null");
        Objects.requireNonNull(request, "创建项目请求不能为 null");

        authorizationService.requireAtLeast(
                request.teamId(),
                currentUserId,
                TeamRole.ADMIN
        );
        Project project = Project.create(
                idGenerator.nextId(ResourceType.PROJECT),
                request.teamId(),
                request.name(),
                request.projectKey(),
                request.description(),
                currentUserId,
                UtcTimeText.now(clock)
        );
        if (projectMapper.countByTeamIdAndProjectKey(
                project.getTeamId(),
                project.getProjectKey()
        ) > 0) {
            throw new BusinessException(
                    ProjectErrorCode.PROJECT_KEY_ALREADY_EXISTS
            );
        }

        try {
            requireSingleInsert(projectMapper.insert(project));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ProjectErrorCode.PROJECT_KEY_ALREADY_EXISTS
            );
        }
        return ProjectResponse.from(project);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> listByTeam(
            String currentUserId,
            String teamId
    ) {
        authorizationService.requireMember(teamId, currentUserId);
        return projectMapper.findByTeamId(teamId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ProjectResponse get(String currentUserId, String projectId) {
        Project project = requireProject(projectId);
        requireProjectMember(currentUserId, project);
        return ProjectResponse.from(project);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ProjectResponse update(
            String currentUserId,
            String projectId,
            UpdateProjectRequest request
    ) {
        Objects.requireNonNull(request, "修改项目请求不能为 null");

        Project project = requireProjectForUpdate(projectId);
        requireProjectAdministrator(currentUserId, project);
        if (request.version() != project.getVersion()) {
            throw new BusinessException(
                    ProjectErrorCode.PROJECT_VERSION_CONFLICT
            );
        }
        if (!project.getStatus().canTransitionTo(request.status())) {
            throw new BusinessException(
                    ProjectErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        int expectedVersion = project.getVersion();
        boolean changed = project.updateDetails(
                request.name(),
                request.description(),
                request.status(),
                UtcTimeText.now(clock)
        );
        if (!changed) {
            return ProjectResponse.from(project);
        }

        requireSuccessfulUpdate(
                projectMapper.update(project, expectedVersion)
        );
        return ProjectResponse.from(requireProject(projectId));
    }
}
