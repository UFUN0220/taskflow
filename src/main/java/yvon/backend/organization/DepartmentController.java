package yvon.backend.organization;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yvon.backend.common.api.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<List<DepartmentNode>> tree() {
        return ApiResponse.success(departmentService.activeTree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('department:write')")
    public ApiResponse<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.success(departmentService.create(request));
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize("hasAuthority('department:write')")
    public ApiResponse<DepartmentResponse> update(@PathVariable Long departmentId,
                                                   @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.success(departmentService.update(departmentId, request));
    }
}
