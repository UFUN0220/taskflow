package yvon.backend.notification;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import yvon.backend.common.mybatis.AuditEntity;

import java.time.LocalDateTime;

@TableName("notification")
public class NotificationEntity extends AuditEntity {
    @TableField("source_message_id")
    private String sourceMessageId;
    @TableField("user_id")
    private Long userId;
    @TableField("notification_type")
    private String notificationType;
    private String title;
    private String content;
    @TableField("aggregate_type")
    private String aggregateType;
    @TableField("aggregate_id")
    private Long aggregateId;
    @TableField("read_at")
    private LocalDateTime readAt;
    private String status;

    public String getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(String sourceMessageId) { this.sourceMessageId = sourceMessageId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
