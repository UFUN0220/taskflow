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
        private String taskStatusRoutingKey = "task.status.changed";
        private String retryExchange = "taskflow.reminder.retry";
        private String retryQueue = "taskflow.reminder.retry";
        private String retryRoutingKey = "reminder.retry";
        private String deadLetterExchange = "taskflow.reminder.dlx";
        private String deadLetterQueue = "taskflow.reminder.dead";
        private String deadLetterRoutingKey = "reminder.dead";
        private int maxAttempts = 3;
        private long retryDelayMs = 5000;

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getQueue() { return queue; }
        public void setQueue(String queue) { this.queue = queue; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
        public String getTaskStatusRoutingKey() { return taskStatusRoutingKey; }
        public void setTaskStatusRoutingKey(String taskStatusRoutingKey) { this.taskStatusRoutingKey = taskStatusRoutingKey; }
        public String getRetryExchange() { return retryExchange; }
        public void setRetryExchange(String retryExchange) { this.retryExchange = retryExchange; }
        public String getRetryQueue() { return retryQueue; }
        public void setRetryQueue(String retryQueue) { this.retryQueue = retryQueue; }
        public String getRetryRoutingKey() { return retryRoutingKey; }
        public void setRetryRoutingKey(String retryRoutingKey) { this.retryRoutingKey = retryRoutingKey; }
        public String getDeadLetterExchange() { return deadLetterExchange; }
        public void setDeadLetterExchange(String deadLetterExchange) { this.deadLetterExchange = deadLetterExchange; }
        public String getDeadLetterQueue() { return deadLetterQueue; }
        public void setDeadLetterQueue(String deadLetterQueue) { this.deadLetterQueue = deadLetterQueue; }
        public String getDeadLetterRoutingKey() { return deadLetterRoutingKey; }
        public void setDeadLetterRoutingKey(String deadLetterRoutingKey) { this.deadLetterRoutingKey = deadLetterRoutingKey; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }
}
