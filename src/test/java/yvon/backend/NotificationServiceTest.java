package yvon.backend;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import yvon.backend.notification.NotificationEntity;
import yvon.backend.notification.NotificationMapper;
import yvon.backend.notification.NotificationService;
import yvon.backend.reminder.ReminderDueMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private final NotificationMapper mapper = mock(NotificationMapper.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NotificationService service = new NotificationService(mapper, jdbcTemplate);

    @Test
    void reminderCreatesOneIdempotentNotificationPerActiveAssignee() {
        when(jdbcTemplate.queryForList(anyString(), any(Long.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("task_no")) {
                return List.of(Map.of("task_no", "TASK-7", "title", "Prepare report",
                        "due_at", "2026-08-08 10:00:00", "status", "IN_PROGRESS"));
            }
            return List.of(11L, 12L);
        });
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Long.class)))
                .thenReturn(List.of(11L, 12L));

        service.handleReminder(new ReminderDueMessage("17", 17L, 7L, "DUE_SOON",
                LocalDateTime.of(2026, 8, 8, 10, 0)));

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(mapper, times(2)).insertIdempotent(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationEntity::getUserId)
                .containsExactly(11L, 12L);
        assertThat(captor.getAllValues()).allSatisfy(notification -> {
            assertThat(notification.getSourceMessageId()).isEqualTo("17");
            assertThat(notification.getNotificationType()).isEqualTo("TASK_REMINDER_DUE_SOON");
        });
    }

    @Test
    void terminalTaskDoesNotGenerateReminderNotification() {
        when(jdbcTemplate.queryForList(anyString(), any(Long.class)))
                .thenReturn(List.of(Map.of("task_no", "TASK-7", "title", "Done", "due_at", "x", "status", "COMPLETED")));

        service.handleReminder(new ReminderDueMessage("17", 17L, 7L, "OVERDUE",
                LocalDateTime.of(2026, 8, 8, 10, 0)));

        verifyNoInteractions(mapper);
    }
}
