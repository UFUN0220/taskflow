package yvon.backend.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ProjectCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{2,63}") String projectCode,
        @NotBlank @Size(max = 200) String projectName,
        Long departmentId,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
