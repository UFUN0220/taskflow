package yvon.backend.reminder;

public interface ReminderMessagePublisher {
    void publish(ReminderDueMessage message);
}
