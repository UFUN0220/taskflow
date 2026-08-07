package yvon.backend.task;

import jakarta.validation.constraints.NotNull;

public record TaskTransitionRequest(@NotNull Integer version) {
}
