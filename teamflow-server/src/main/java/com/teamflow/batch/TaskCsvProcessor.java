package com.teamflow.batch;

import com.teamflow.common.error.BusinessException;
import com.teamflow.core.auth.support.LoginIdentity;
import com.teamflow.core.common.id.ReadableIdGenerator;
import com.teamflow.core.common.id.ResourceType;
import com.teamflow.core.common.time.UtcTimeText;
import com.teamflow.core.importjob.error.ImportJobErrorCode;
import com.teamflow.core.project.domain.Project;
import com.teamflow.core.project.domain.ProjectStatus;
import com.teamflow.core.project.mapper.ProjectMapper;
import com.teamflow.core.task.domain.Task;
import com.teamflow.core.team.mapper.TeamMemberMapper;
import com.teamflow.core.user.domain.User;
import com.teamflow.core.user.mapper.UserMapper;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.batch.item.ItemProcessor;

/** 校验 CSV 单行，并把负责人邮箱解析成团队成员用户编号。 */
public class TaskCsvProcessor implements ItemProcessor<TaskCsvRow, Task> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    private final UserMapper userMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ProjectMapper projectMapper;
    private final ReadableIdGenerator idGenerator;
    private final Clock clock;
    private final String projectId;
    private final String reporterId;
    private final Map<String, Optional<String>> assigneeCache =
            new HashMap<>();

    private Project project;

    public TaskCsvProcessor(
            UserMapper userMapper,
            TeamMemberMapper teamMemberMapper,
            ProjectMapper projectMapper,
            ReadableIdGenerator idGenerator,
            Clock clock,
            String projectId,
            String reporterId
    ) {
        this.userMapper = Objects.requireNonNull(
                userMapper,
                "用户 Mapper 不能为 null"
        );
        this.teamMemberMapper = Objects.requireNonNull(
                teamMemberMapper,
                "团队成员 Mapper 不能为 null"
        );
        this.projectMapper = Objects.requireNonNull(
                projectMapper,
                "项目 Mapper 不能为 null"
        );
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "编号生成器不能为 null"
        );
        this.clock = Objects.requireNonNull(clock, "任务时钟不能为 null");
        this.projectId = requireParameter(projectId, "projectId");
        this.reporterId = requireParameter(reporterId, "reporterId");
    }

    @Override
    public Task process(TaskCsvRow item) {
        Objects.requireNonNull(item, "CSV 行不能为 null");
        Project activeProject = activeProject();
        String assigneeId = resolveAssignee(
                item.rowNumber(),
                item.assigneeEmail(),
                activeProject.getTeamId()
        );
        try {
            return Task.create(
                    idGenerator.nextId(ResourceType.TASK),
                    activeProject.getId(),
                    item.title(),
                    item.description(),
                    item.priority(),
                    assigneeId,
                    reporterId,
                    item.dueAt(),
                    UtcTimeText.now(clock)
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new CsvRowValidationException(
                    item.rowNumber(),
                    exception.getMessage() == null
                            ? "任务字段不合法"
                            : exception.getMessage(),
                    exception
            );
        }
    }

    private Project activeProject() {
        if (project != null) {
            return project;
        }
        Project loaded = projectMapper.findById(projectId)
                .orElseThrow(() -> new IllegalStateException(
                        "批处理启动后项目不存在: " + projectId
                ));
        if (loaded.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessException(
                    ImportJobErrorCode.PROJECT_NOT_ACTIVE
            );
        }
        if (teamMemberMapper.findByTeamAndUser(
                loaded.getTeamId(),
                reporterId
        ).isEmpty()) {
            throw new IllegalStateException(
                    "导入任务创建者已不属于项目团队: " + reporterId
            );
        }
        project = loaded;
        return loaded;
    }

    private String resolveAssignee(
            long rowNumber,
            String email,
            String teamId
    ) {
        if (email == null) {
            return null;
        }
        final String normalizedEmail;
        try {
            normalizedEmail = LoginIdentity.normalize(email);
        } catch (RuntimeException exception) {
            throw new CsvRowValidationException(
                    rowNumber,
                    "assigneeEmail 不合法",
                    exception
            );
        }
        if (normalizedEmail.length() > 128
                || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new CsvRowValidationException(
                    rowNumber,
                    "assigneeEmail 不是有效邮箱"
            );
        }

        Optional<String> cached = assigneeCache.computeIfAbsent(
                normalizedEmail,
                key -> findTeamMemberId(key, teamId)
        );
        return cached.orElseThrow(() -> new CsvRowValidationException(
                rowNumber,
                "负责人不存在或不属于项目团队"
        ));
    }

    private Optional<String> findTeamMemberId(String email, String teamId) {
        return userMapper.findByIdentifier(email)
                .filter(user -> email.equals(user.getEmail()))
                .map(User::getId)
                .filter(userId -> teamMemberMapper.findByTeamAndUser(
                        teamId,
                        userId
                ).isPresent());
    }

    private static String requireParameter(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "JobParameter " + name + " 不能为空"
            );
        }
        return value;
    }
}
