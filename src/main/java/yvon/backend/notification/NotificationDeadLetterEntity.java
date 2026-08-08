package yvon.backend.notification;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import yvon.backend.common.mybatis.AuditEntity;

import java.time.LocalDateTime;

@TableName("notification_dead_letter")
public class NotificationDeadLetterEntity extends AuditEntity {
    @TableField("message_id")
    private String messageId;
    @TableField("event_type")
    private String eventType;
    @TableField("trace_id")
    private String traceId;
    @TableField("payload_json")
    private String payloadJson;
    @TableField("plan_id")
    private Long planId;
    @TableField("task_id")
    private Long taskId;
    @TableField("error_reason")
    private String errorReason;
    @TableField("retry_count")
    private Integer retryCount;
    private String status;
    @TableField("last_failed_at")
    private LocalDateTime lastFailedAt;
    @TableField("replayed_at")
    private LocalDateTime replayedAt;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastFailedAt() { return lastFailedAt; }
    public void setLastFailedAt(LocalDateTime lastFailedAt) { this.lastFailedAt = lastFailedAt; }
    public LocalDateTime getReplayedAt() { return replayedAt; }
    public void setReplayedAt(LocalDateTime replayedAt) { this.replayedAt = replayedAt; }
}
