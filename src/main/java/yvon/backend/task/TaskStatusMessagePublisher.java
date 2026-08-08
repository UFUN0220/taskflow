package yvon.backend.task;

public interface TaskStatusMessagePublisher {
    void publish(TaskStatusChangedMessage message);
}
