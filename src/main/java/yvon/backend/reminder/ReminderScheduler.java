package yvon.backend.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "taskflow.reminder.enabled", havingValue = "true")
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderRedisIndexService indexService;
    private final ReminderPlanMapper planMapper;
    private final ReminderMessagePublisher publisher;
    private final ReminderRedisLock lock;
    private final Clock clock;

    public ReminderScheduler(ReminderRedisIndexService indexService, ReminderPlanMapper planMapper,
                             ReminderMessagePublisher publisher, ReminderRedisLock lock,
                             ReminderProperties properties, Clock clock) {
        this.indexService = indexService;
        this.planMapper = planMapper;
        this.publisher = publisher;
        this.lock = lock;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${taskflow.reminder.scan-delay-ms:10000}")
    public void scanDuePlans() {
        String token = lock.tryAcquire();
        if (token == null) return;
        try {
            for (String planId : indexService.claimDue(Instant.now(clock))) {
                emit(planId);
            }
        } finally {
            lock.release(token);
        }
    }

    @Scheduled(fixedDelayString = "${taskflow.reminder.rebuild-delay-ms:300000}", initialDelayString = "${taskflow.reminder.rebuild-delay-ms:300000}")
    public void rebuildIndex() {
        String token = lock.tryAcquire();
        if (token == null) return;
        try {
            indexService.rebuildFromDatabase();
        } finally {
            lock.release(token);
        }
    }

    private void emit(String planId) {
        long numericId;
        try {
            numericId = Long.parseLong(planId);
        } catch (NumberFormatException exception) {
            log.warn("Ignoring malformed reminder plan id from Redis: {}", planId);
            return;
        }
        ReminderPlanEntity plan = planMapper.selectById(numericId);
        if (plan == null || !"PLANNED".equals(plan.getStatus())) return;
        Instant now = Instant.now(clock);
        if (plan.getTriggerAt().atZone(java.time.ZoneId.systemDefault()).toInstant().isAfter(now)) {
            indexService.requeue(plan);
            return;
        }
        try {
            publisher.publish(new ReminderDueMessage(plan.getId().toString(), plan.getId(), plan.getTaskId(),
                    plan.getReminderType(), plan.getTriggerAt()));
            if (planMapper.markEmitted(plan.getId(), plan.getVersion()) == 0) {
                log.warn("Reminder plan changed before emitted state update, planId={}", plan.getId());
            }
        } catch (RuntimeException exception) {
            planMapper.markFailed(plan.getId(), plan.getVersion());
            log.error("Reminder message publish failed, planId={}", plan.getId(), exception);
        }
    }
}
