package yvon.backend.comment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.organization.PageResponse;

@RestController
@Validated
@RequestMapping("/api/tasks/{taskId}/comments")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class TaskCommentController {

    private final TaskCommentService commentService;

    public TaskCommentController(TaskCommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('task:comment:create')")
    public ApiResponse<TaskCommentResponse> create(@PathVariable Long taskId,
                                                    @Valid @RequestBody TaskCommentCreateRequest request,
                                                    org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(commentService.addUserComment(taskId, request, principal(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:comment:read')")
    public ApiResponse<PageResponse<TaskCommentResponse>> page(@PathVariable Long taskId,
                                                                @RequestParam(defaultValue = "1") @Min(1) long page,
                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
                                                                org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(commentService.page(taskId, page, size, principal(authentication)));
    }

    private UserPrincipal principal(org.springframework.security.core.Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
