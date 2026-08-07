package yvon.backend.task;

import java.util.Map;

public final class TaskStateMachine {

    private static final Map<TaskCommand, Map<TaskStatus, TaskStatus>> TRANSITIONS = Map.of(
            TaskCommand.SUBMIT, Map.of(TaskStatus.DRAFT, TaskStatus.PENDING_ACCEPTANCE),
            TaskCommand.ACCEPT, Map.of(TaskStatus.PENDING_ACCEPTANCE, TaskStatus.IN_PROGRESS),
            TaskCommand.SUBMIT_REVIEW, Map.of(TaskStatus.IN_PROGRESS, TaskStatus.PENDING_REVIEW),
            TaskCommand.APPROVE, Map.of(TaskStatus.PENDING_REVIEW, TaskStatus.COMPLETED),
            TaskCommand.REJECT, Map.of(TaskStatus.PENDING_REVIEW, TaskStatus.REJECTED),
            TaskCommand.START, Map.of(TaskStatus.REJECTED, TaskStatus.IN_PROGRESS),
            TaskCommand.CANCEL, Map.of(
                    TaskStatus.DRAFT, TaskStatus.CANCELLED,
                    TaskStatus.PENDING_ACCEPTANCE, TaskStatus.CANCELLED,
                    TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED,
                    TaskStatus.REJECTED, TaskStatus.CANCELLED),
            TaskCommand.ARCHIVE, Map.of(TaskStatus.COMPLETED, TaskStatus.ARCHIVED,
                    TaskStatus.CANCELLED, TaskStatus.ARCHIVED)
    );

    private TaskStateMachine() {
    }

    public static boolean canTransition(TaskStatus from, TaskCommand command) {
        return TRANSITIONS.getOrDefault(command, Map.of()).containsKey(from);
    }

    public static TaskStatus target(TaskStatus from, TaskCommand command) {
        TaskStatus target = TRANSITIONS.getOrDefault(command, Map.of()).get(from);
        if (target == null) {
            throw new IllegalStateException("任务状态不允许执行命令: " + command + " from " + from);
        }
        return target;
    }
}
