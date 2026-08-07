package yvon.backend.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TaskUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        LocalDateTime dueAt,
        @NotNull Integer version
) {
}
