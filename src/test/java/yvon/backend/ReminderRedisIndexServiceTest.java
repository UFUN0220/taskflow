package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import yvon.backend.reminder.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class ReminderRedisIndexServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zset = mock(ZSetOperations.class);
    private final ReminderPlanMapper mapper = mock(ReminderPlanMapper.class);
    private final ReminderProperties properties = new ReminderProperties();
    private final ReminderRedisIndexService service = new ReminderRedisIndexService(redis, mapper, properties);

    ReminderRedisIndexServiceTest() {
        when(redis.opsForZSet()).thenReturn(zset);
    }

    @Test
    void rebuildUsesDatabasePlansAsSourceOfTruthAfterRedisLoss() {
        ReminderPlanEntity plan = plan(21L, "PLANNED");
        when(mapper.selectAllPlanned()).thenReturn(List.of(plan));

        service.rebuildFromDatabase();

        verify(redis).delete("taskflow:reminders:due");
        verify(zset).add(eq("taskflow:reminders:due"), eq("21"), anyDouble());
    }

    @Test
    void taskReindexRemovesOldMembersAndAddsOnlyPlannedMembers() {
        ReminderPlanEntity old = plan(22L, "CANCELLED");
        ReminderPlanEntity active = plan(23L, "PLANNED");
        when(mapper.selectByTaskId(9L)).thenReturn(List.of(old, active));

        service.reindexTask(9L);

        verify(zset).remove("taskflow:reminders:due", "22");
        verify(zset).remove("taskflow:reminders:due", "23");
        verify(zset).add(eq("taskflow:reminders:due"), eq("23"), anyDouble());
        verify(zset, never()).add(eq("taskflow:reminders:due"), eq("22"), anyDouble());
    }

    private ReminderPlanEntity plan(Long id, String status) {
        ReminderPlanEntity plan = new ReminderPlanEntity();
        plan.setId(id);
        plan.setTaskId(9L);
        plan.setTriggerAt(LocalDateTime.of(2026, 8, 7, 10, 0));
        plan.setStatus(status);
        return plan;
    }
}
