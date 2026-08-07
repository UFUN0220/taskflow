package yvon.backend;

import org.junit.jupiter.api.Test;
import yvon.backend.task.TaskCommand;
import yvon.backend.task.TaskStateMachine;
import yvon.backend.task.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStateMachineTest {

    @Test
    void supportsOnlyTheDocumentedLifecycleTransitions() {
        assertThat(TaskStateMachine.target(TaskStatus.DRAFT, TaskCommand.SUBMIT))
                .isEqualTo(TaskStatus.PENDING_ACCEPTANCE);
        assertThat(TaskStateMachine.target(TaskStatus.PENDING_ACCEPTANCE, TaskCommand.ACCEPT))
                .isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(TaskStateMachine.target(TaskStatus.IN_PROGRESS, TaskCommand.SUBMIT_REVIEW))
                .isEqualTo(TaskStatus.PENDING_REVIEW);
        assertThat(TaskStateMachine.target(TaskStatus.PENDING_REVIEW, TaskCommand.APPROVE))
                .isEqualTo(TaskStatus.COMPLETED);
        assertThat(TaskStateMachine.target(TaskStatus.PENDING_REVIEW, TaskCommand.REJECT))
                .isEqualTo(TaskStatus.REJECTED);
        assertThat(TaskStateMachine.target(TaskStatus.REJECTED, TaskCommand.START))
                .isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(TaskStateMachine.target(TaskStatus.COMPLETED, TaskCommand.ARCHIVE))
                .isEqualTo(TaskStatus.ARCHIVED);
        assertThat(TaskStateMachine.canTransition(TaskStatus.COMPLETED, TaskCommand.CANCEL)).isFalse();
    }

    @Test
    void rejectsCommandsThatWouldSkipAReviewOrReopenTerminalState() {
        assertThatThrownBy(() -> TaskStateMachine.target(TaskStatus.DRAFT, TaskCommand.APPROVE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TaskStateMachine.target(TaskStatus.ARCHIVED, TaskCommand.START))
                .isInstanceOf(IllegalStateException.class);
    }
}
