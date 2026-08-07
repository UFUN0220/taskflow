package yvon.backend.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String employeeNo,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(min = 8, max = 72) String password,
        Long departmentId,
        @Size(max = 32) List<@NotBlank @Size(max = 64) String> roleCodes
) {
}
