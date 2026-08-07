package yvon.backend.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.organization.PageResponse;

@RestController
@Validated
@RequestMapping("/api/tasks")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('task:create')")
    public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request,
                                             org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(taskService.create(request, principal(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<PageResponse<TaskResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @jakarta.validation.constraints.Pattern(regexp = "DRAFT|PENDING_ACCEPTANCE|IN_PROGRESS|PENDING_REVIEW|REJECTED|COMPLETED|CANCELLED|ARCHIVED") String status,
            @RequestParam(required = false) @jakarta.validation.constraints.Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dueTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime createdTo,
            org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(taskService.page(new TaskPageQuery(page, size, title, status, priority, assigneeId,
                creatorId, departmentId, projectId, dueFrom, dueTo, createdFrom, createdTo), principal(authentication)));
    }

    @PatchMapping("/{taskId}")
    @PreAuthorize("hasAuthority('task:update')")
    public ApiResponse<TaskResponse> updateDraft(@PathVariable Long taskId, @Valid @RequestBody TaskUpdateRequest request,
                                                  org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(taskService.updateDraft(taskId, request, principal(authentication)));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAuthority('task:delete')")
    public ApiResponse<Void> deleteDraft(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                          org.springframework.security.core.Authentication authentication) {
        taskService.deleteDraft(taskId, request, principal(authentication));
        return ApiResponse.success(null);
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAuthority('task:read')")
    public ApiResponse<TaskResponse> get(@PathVariable Long taskId,
                                         org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(taskService.get(taskId, principal(authentication)));
    }

    @PostMapping("/{taskId}/submit")
    @PreAuthorize("hasAuthority('task:submit')")
    public ApiResponse<TaskResponse> submit(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                            org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.SUBMIT, request, authentication);
    }

    @PostMapping("/{taskId}/accept")
    @PreAuthorize("hasAuthority('task:accept')")
    public ApiResponse<TaskResponse> accept(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                            org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.ACCEPT, request, authentication);
    }

    @PostMapping("/{taskId}/submit-review")
    @PreAuthorize("hasAuthority('task:review')")
    public ApiResponse<TaskResponse> submitReview(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                                   org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.SUBMIT_REVIEW, request, authentication);
    }

    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasAuthority('task:approve')")
    public ApiResponse<TaskResponse> approve(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                              org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.APPROVE, request, authentication);
    }

    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasAuthority('task:approve')")
    public ApiResponse<TaskResponse> complete(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                               org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.APPROVE, request, authentication);
    }

    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasAuthority('task:approve')")
    public ApiResponse<TaskResponse> reject(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                             org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.REJECT, request, authentication);
    }

    @PostMapping("/{taskId}/start")
    @PreAuthorize("hasAuthority('task:submit')")
    public ApiResponse<TaskResponse> start(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                           org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.START, request, authentication);
    }

    @PostMapping("/{taskId}/cancel")
    @PreAuthorize("hasAuthority('task:cancel')")
    public ApiResponse<TaskResponse> cancel(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                             org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.CANCEL, request, authentication);
    }

    @PostMapping("/{taskId}/archive")
    @PreAuthorize("hasAuthority('task:archive')")
    public ApiResponse<TaskResponse> archive(@PathVariable Long taskId, @Valid @RequestBody TaskTransitionRequest request,
                                              org.springframework.security.core.Authentication authentication) {
        return transition(taskId, TaskCommand.ARCHIVE, request, authentication);
    }

    @PostMapping("/{taskId}/transfer")
    @PreAuthorize("hasAuthority('task:assign')")
    public ApiResponse<TaskResponse> transfer(@PathVariable Long taskId, @Valid @RequestBody TransferTaskRequest request,
                                               org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(taskService.transfer(taskId, request, principal(authentication)));
    }

    private ApiResponse<TaskResponse> transition(Long taskId, TaskCommand command, TaskTransitionRequest request,
                                                 org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(taskService.transition(taskId, command, request, principal(authentication)));
    }

    private UserPrincipal principal(org.springframework.security.core.Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
