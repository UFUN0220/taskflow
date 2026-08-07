package yvon.backend.reminder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.task.TaskEntity;
import yvon.backend.task.TaskStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderPlanService {

    private final ReminderPlanMapper mapper;
    private final ReminderProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReminderPlanService(ReminderPlanMapper mapper, ReminderProperties properties,
                               ApplicationEventPublisher eventPublisher, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void syncForTask(TaskEntity task) {
        mapper.cancelPlanned(task.getId());
        List<ReminderPlanEntity> plans = buildPlans(task, LocalDateTime.now(clock));
        for (ReminderPlanEntity plan : plans) mapper.insertOrReactivate(plan);
        eventPublisher.publishEvent(new ReminderIndexChangedEvent(task.getId()));
    }

    @Transactional
    public void cancelForTask(Long taskId) {
        mapper.cancelPlanned(taskId);
        eventPublisher.publishEvent(new ReminderIndexChangedEvent(taskId));
    }

    private List<ReminderPlanEntity> buildPlans(TaskEntity task, LocalDateTime now) {
        if (task.getDueAt() == null || terminal(task.getStatus())) return List.of();
        List<ReminderPlanEntity> plans = new ArrayList<>();
        if (task.getDueAt().isAfter(now)) {
            plans.add(plan(task, "DUE_SOON", task.getDueAt().minus(properties.getDueSoonLeadTime())));
        }
        plans.add(plan(task, "OVERDUE", task.getDueAt()));
        return plans;
    }

    private ReminderPlanEntity plan(TaskEntity task, String type, LocalDateTime triggerAt) {
        ReminderPlanEntity plan = new ReminderPlanEntity();
        plan.setTaskId(task.getId());
        plan.setReminderType(type);
        plan.setTriggerAt(triggerAt);
        plan.setStatus("PLANNED");
        plan.setVersion(0);
        return plan;
    }

    private boolean terminal(String status) {
        return TaskStatus.COMPLETED.name().equals(status)
                || TaskStatus.CANCELLED.name().equals(status)
                || TaskStatus.ARCHIVED.name().equals(status);
    }
}
