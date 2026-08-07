package yvon.backend.project;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.api.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public ApiResponse<List<ProjectResponse>> list(org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(projectService.list((UserPrincipal) authentication.getPrincipal()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:write')")
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request,
                                                org.springframework.security.core.Authentication authentication) {
        return ApiResponse.success(projectService.create(request, (UserPrincipal) authentication.getPrincipal()));
    }

    @PutMapping("/{projectId}/members")
    @PreAuthorize("hasAuthority('project:member:write')")
    public ApiResponse<Void> addMember(@PathVariable Long projectId, @Valid @RequestBody ProjectMemberRequest request,
                                       org.springframework.security.core.Authentication authentication) {
        projectService.addMember(projectId, request, (UserPrincipal) authentication.getPrincipal());
        return ApiResponse.success(null);
    }
}
