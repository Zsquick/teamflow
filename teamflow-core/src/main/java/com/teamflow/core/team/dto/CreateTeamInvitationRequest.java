package com.teamflow.core.team.dto;

import com.teamflow.core.team.domain.TeamRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 发送团队邀请的请求。 */
public record CreateTeamInvitationRequest(
        @NotBlank @Size(max = 128) String identifier,
        @NotNull TeamRole role
) {
}
