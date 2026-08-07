package yvon.backend.reminder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "taskflow.reminder.enabled", havingValue = "true")
public class ReminderRedisIndexService {

    private final StringRedisTemplate redis;
    private final ReminderPlanMapper mapper;
    private final ReminderProperties properties;

    public ReminderRedisIndexService(StringRedisTemplate redis, ReminderPlanMapper mapper,
                                     ReminderProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIndexChanged(ReminderIndexChangedEvent event) {
        reindexTask(event.taskId());
    }

    public void reindexTask(Long taskId) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        List<ReminderPlanEntity> plans = mapper.selectByTaskId(taskId);
        for (ReminderPlanEntity plan : plans) {
            zset.remove(properties.getRedisKey(), plan.getId().toString());
        }
        for (ReminderPlanEntity plan : plans) {
            if ("PLANNED".equals(plan.getStatus())) {
                zset.add(properties.getRedisKey(), plan.getId().toString(), score(plan));
            }
        }
    }

    public Set<String> claimDue(Instant now) {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        Set<String> candidates = zset.rangeByScore(properties.getRedisKey(), 0, now.toEpochMilli(),
                0, properties.getScanBatchSize());
        if (candidates == null || candidates.isEmpty()) return Set.of();
        for (String planId : candidates) zset.remove(properties.getRedisKey(), planId);
        return candidates;
    }

    public void requeue(ReminderPlanEntity plan) {
        redis.opsForZSet().add(properties.getRedisKey(), plan.getId().toString(), score(plan));
    }

    public void rebuildFromDatabase() {
        ZSetOperations<String, String> zset = redis.opsForZSet();
        redis.delete(properties.getRedisKey());
        List<ReminderPlanEntity> plans = mapper.selectAllPlanned();
        for (ReminderPlanEntity plan : plans) zset.add(properties.getRedisKey(), plan.getId().toString(), score(plan));
    }

    private double score(ReminderPlanEntity plan) {
        return plan.getTriggerAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
