package yvon.backend.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @NotBlank @Size(max = 128) String departmentName,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @NotNull Integer version
) {
}
