package yvon.backend.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoleCreateRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,63}") String roleCode,
        @NotBlank @Size(max = 128) String roleName,
        @NotEmpty @Size(max = 64) List<@NotBlank @Pattern(regexp = "[a-z][a-z0-9:_-]{1,127}") String> permissionCodes,
        @NotNull DataScopeType scopeType
) {
}
