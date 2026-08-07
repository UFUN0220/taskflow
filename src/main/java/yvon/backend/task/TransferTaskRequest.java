package yvon.backend.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TransferTaskRequest(
        @NotNull Integer version,
        @NotNull Long primaryAssigneeId,
        @Size(max = 50) List<Long> collaboratorIds
) {
}
