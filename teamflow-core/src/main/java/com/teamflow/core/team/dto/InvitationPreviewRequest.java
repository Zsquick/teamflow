package com.teamflow.core.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 精确查找被邀请用户的请求。 */
public record InvitationPreviewRequest(
        @NotBlank @Size(max = 128) String identifier
) {
}
