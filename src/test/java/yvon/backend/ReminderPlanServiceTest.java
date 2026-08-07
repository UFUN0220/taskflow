package yvon.backend;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import yvon.backend.reminder.ReminderIndexChangedEvent;
import yvon.backend.reminder.ReminderPlanEntity;
import yvon.backend.reminder.ReminderPlanMapper;
import yvon.backend.reminder.ReminderPlanService;
import yvon.backend.reminder.ReminderProperties;
import yvon.backend.task.TaskEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReminderPlanServiceTest {

    private final ReminderPlanMapper mapper = mock(ReminderPlanMapper.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ReminderProperties properties = new ReminderProperties();
    private final ReminderPlanService service = new ReminderPlanService(mapper, properties, eventPublisher,
            Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("UTC")));

    @Test
    void createsDueSoonAndOverduePlansForTaskWithFutureDeadline() {
        TaskEntity task = task("DRAFT", LocalDateTime.of(2026, 8, 9, 10, 0));

        service.syncForTask(task);

        ArgumentCaptor<ReminderPlanEntity> captor = ArgumentCaptor.forClass(ReminderPlanEntity.class);
        verify(mapper, times(2)).insertOrReactivate(captor.capture());
        assertThat(captor.getAllValues()).extracting(ReminderPlanEntity::getReminderType)
                .containsExactly("DUE_SOON", "OVERDUE");
        assertThat(captor.getAllValues().get(0).getTriggerAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 10, 0));
        verify(mapper).cancelPlanned(9L);
        verify(eventPublisher).publishEvent(new ReminderIndexChangedEvent(9L));
    }

    @Test
    void terminalTaskCancelsOldPlansAndCreatesNoNewPlan() {
        service.syncForTask(task("COMPLETED", LocalDateTime.of(2026, 8, 9, 10, 0)));

        verify(mapper).cancelPlanned(9L);
        verify(mapper, never()).insertOrReactivate(any(ReminderPlanEntity.class));
        verify(eventPublisher).publishEvent(new ReminderIndexChangedEvent(9L));
    }

    private TaskEntity task(String status, LocalDateTime dueAt) {
        TaskEntity task = new TaskEntity();
        task.setId(9L);
        task.setStatus(status);
        task.setDueAt(dueAt);
        return task;
    }
}
