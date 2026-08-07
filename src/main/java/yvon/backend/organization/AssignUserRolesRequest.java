package yvon.backend.organization;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssignUserRolesRequest(
        @NotEmpty @Size(max = 32) List<@NotNull @Size(max = 64) String> roleCodes,
        @NotNull Integer version
) {
}
