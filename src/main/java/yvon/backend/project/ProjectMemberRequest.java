package yvon.backend.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ProjectMemberRequest(
        @NotNull Long userId,
        @NotNull @Pattern(regexp = "MANAGER|MEMBER") String memberRole
) {
}
