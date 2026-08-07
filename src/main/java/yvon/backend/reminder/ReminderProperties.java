package yvon.backend.reminder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "taskflow.reminder")
public class ReminderProperties {

    private boolean enabled;
    private Duration dueSoonLeadTime = Duration.ofHours(24);
    private int scanBatchSize = 100;
    private Duration lockTtl = Duration.ofSeconds(30);
    private String redisKey = "taskflow:reminders:due";
    private String lockKey = "taskflow:reminders:scan-lock";
    private Rabbit rabbit = new Rabbit();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getDueSoonLeadTime() { return dueSoonLeadTime; }
    public void setDueSoonLeadTime(Duration dueSoonLeadTime) { this.dueSoonLeadTime = dueSoonLeadTime; }
    public int getScanBatchSize() { return scanBatchSize; }
    public void setScanBatchSize(int scanBatchSize) { this.scanBatchSize = scanBatchSize; }
    public Duration getLockTtl() { return lockTtl; }
    public void setLockTtl(Duration lockTtl) { this.lockTtl = lockTtl; }
    public String getRedisKey() { return redisKey; }
    public void setRedisKey(String redisKey) { this.redisKey = redisKey; }
    public String getLockKey() { return lockKey; }
    public void setLockKey(String lockKey) { this.lockKey = lockKey; }
    public Rabbit getRabbit() { return rabbit; }
    public void setRabbit(Rabbit rabbit) { this.rabbit = rabbit; }

    public static class Rabbit {
        private String exchange = "taskflow.reminder";
        private String queue = "taskflow.reminder.dispatch";
        private String routingKey = "reminder.due";

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getQueue() { return queue; }
        public void setQueue(String queue) { this.queue = queue; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
    }
}
