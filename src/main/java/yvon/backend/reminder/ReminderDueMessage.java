package yvon.backend.reminder;

import java.time.LocalDateTime;

public record ReminderDueMessage(String messageId, Long planId, Long taskId,
                                 String reminderType, LocalDateTime triggerAt) {
}
