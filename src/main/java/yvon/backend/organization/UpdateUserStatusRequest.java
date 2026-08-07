package yvon.backend.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED|LOCKED") String status,
        @NotNull Integer version
) {
}
