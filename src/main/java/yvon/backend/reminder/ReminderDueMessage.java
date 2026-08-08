package yvon.backend.reminder;

import java.time.LocalDateTime;

public record ReminderDueMessage(String messageId, Long planId, Long taskId,
                                 String reminderType, LocalDateTime triggerAt, String eventType) {
    public ReminderDueMessage(String messageId, Long planId, Long taskId,
                              String reminderType, LocalDateTime triggerAt) {
        this(messageId, planId, taskId, reminderType, triggerAt, "REMINDER_DUE");
    }
}
