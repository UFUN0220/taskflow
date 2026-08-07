package yvon.backend;

import org.junit.jupiter.api.Test;
import yvon.backend.reminder.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReminderSchedulerTest {

    private final ReminderRedisIndexService indexService = mock(ReminderRedisIndexService.class);
    private final ReminderPlanMapper planMapper = mock(ReminderPlanMapper.class);
    private final ReminderMessagePublisher publisher = mock(ReminderMessagePublisher.class);
    private final ReminderRedisLock lock = mock(ReminderRedisLock.class);
    private final ReminderProperties properties = new ReminderProperties();
    private final ReminderScheduler scheduler = new ReminderScheduler(indexService, planMapper, publisher, lock,
            properties, Clock.fixed(Instant.parse("2026-08-07T02:00:00Z"), ZoneId.of("UTC")));

    @Test
    void onlyTheInstanceHoldingDistributedLockScansAndEmitsDuePlan() {
        when(lock.tryAcquire()).thenReturn("token");
        when(indexService.claimDue(any())).thenReturn(Set.of("11"));
        ReminderPlanEntity plan = plan(11L, 7L, "DUE_SOON", LocalDateTime.of(2026, 8, 7, 1, 0));
        when(planMapper.selectById(11L)).thenReturn(plan);
        when(planMapper.markEmitted(11L, 0)).thenReturn(1);

        scheduler.scanDuePlans();

        verify(publisher).publish(new ReminderDueMessage("11", 11L, 7L, "DUE_SOON",
                LocalDateTime.of(2026, 8, 7, 1, 0)));
        verify(planMapper).markEmitted(11L, 0);
        verify(lock).release("token");
    }

    @Test
    void publishFailureMarksPlanFailedWithoutAutomaticInfiniteRetry() {
        when(lock.tryAcquire()).thenReturn("token");
        when(indexService.claimDue(any())).thenReturn(Set.of("12"));
        ReminderPlanEntity plan = plan(12L, 7L, "OVERDUE", LocalDateTime.of(2026, 8, 7, 1, 0));
        when(planMapper.selectById(12L)).thenReturn(plan);
        doThrow(new IllegalStateException("broker down")).when(publisher).publish(any());

        scheduler.scanDuePlans();

        verify(planMapper).markFailed(12L, 0);
        verify(indexService, never()).requeue(any());
    }

    @Test
    void noLockMeansNoRedisScan() {
        when(lock.tryAcquire()).thenReturn(null);

        scheduler.scanDuePlans();

        verifyNoInteractions(indexService, planMapper, publisher);
    }

    private ReminderPlanEntity plan(Long id, Long taskId, String type, LocalDateTime triggerAt) {
        ReminderPlanEntity plan = new ReminderPlanEntity();
        plan.setId(id);
        plan.setTaskId(taskId);
        plan.setReminderType(type);
        plan.setTriggerAt(triggerAt);
        plan.setStatus("PLANNED");
        plan.setVersion(0);
        return plan;
    }
}
