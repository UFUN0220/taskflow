package yvon.backend.task;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "taskflow.reminder.enabled", havingValue = "true")
public class TaskStatusMessageEventListener {

    private final TaskStatusMessagePublisher publisher;

    public TaskStatusMessageEventListener(TaskStatusMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(TaskStatusChangedEvent event) {
        publisher.publish(event.message());
    }
}
