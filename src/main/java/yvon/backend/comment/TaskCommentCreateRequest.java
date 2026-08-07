package yvon.backend.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCommentCreateRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 5000, message = "评论内容不能超过5000个字符")
        String content
) {
}
