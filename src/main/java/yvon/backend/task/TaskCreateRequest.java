package yvon.backend.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record TaskCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]{2,63}") String taskNo,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        Long projectId,
        Long departmentId,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        LocalDateTime dueAt,
        Long primaryAssigneeId,
        @Size(max = 50) List<Long> collaboratorIds
) {
}
