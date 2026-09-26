package com.teamflow.core.task.service;

import com.teamflow.core.project.domain.Project;

/**
 * 集中完成项目、任务和团队成员关系的访问校验。
 *
 * <p>后续评论、附件、导入与统计功能复用本边界，避免重复实现同一条
 * task -&gt; project -&gt; team 权限链。</p>
 */
public interface TaskAccessService {

    Project requireProjectMember(
            String currentUserId,
            String projectId
    );

    default Project requireProjectMemberForUpdate(
            String currentUserId,
            String projectId
    ) {
        return requireProjectMemberForUpdate(
                currentUserId,
                projectId,
                null
        );
    }

    Project requireProjectMemberForUpdate(
            String currentUserId,
            String projectId,
            String relatedUserId
    );

    TaskAccessContext requireTaskMember(
            String currentUserId,
            String taskId
    );

    default TaskAccessContext requireTaskMemberForUpdate(
            String currentUserId,
            String taskId
    ) {
        return requireTaskMemberForUpdate(currentUserId, taskId, null);
    }

    TaskAccessContext requireTaskMemberForUpdate(
            String currentUserId,
            String taskId,
            String relatedUserId
    );
}
