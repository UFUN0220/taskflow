package yvon.backend.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_-]{1,63}") String departmentCode,
        @NotBlank @Size(max = 128) String departmentName,
        Long parentId
) {
}
