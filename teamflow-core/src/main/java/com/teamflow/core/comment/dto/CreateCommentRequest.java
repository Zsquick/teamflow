package com.teamflow.core.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增任务评论请求。
 *
 * @param content 评论正文
 */
public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String content
) {

    /** 在 Validation 校验前统一清理用户输入的首尾空白。 */
    public CreateCommentRequest {
        if (content != null) {
            content = content.strip();
        }
    }
}
