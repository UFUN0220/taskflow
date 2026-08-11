package yvon.backend.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.organization.PageResponse;
import yvon.backend.reminder.ReminderDueMessage;
import yvon.backend.task.TaskStatusChangedMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationDeliveryDiagnostics diagnostics;

    public NotificationService(NotificationMapper notificationMapper, JdbcTemplate jdbcTemplate) {
        this(notificationMapper, jdbcTemplate, null, null);
    }

    @Autowired
    public NotificationService(NotificationMapper notificationMapper, JdbcTemplate jdbcTemplate,
                               ApplicationEventPublisher eventPublisher,
                               ObjectProvider<NotificationDeliveryDiagnostics> diagnosticsProvider) {
        this.notificationMapper = notificationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
        this.diagnostics = diagnosticsProvider == null ? null : diagnosticsProvider.getIfAvailable();
    }

    @Transactional
    public void handleReminder(ReminderDueMessage message) {
        Map<String, Object> task = findTask(message.taskId());
        if (task == null || isTerminal((String) task.get("status"))) return;
        List<Long> recipients = assignees(message.taskId(), false);
        String title = "OVERDUE".equals(message.reminderType()) ? "任务已逾期" : "任务即将到期";
        String content = String.format("任务[%s] %s，截止时间：%s",
                task.get("task_no"), task.get("title"), task.get("due_at"));
        insertForRecipients(message.messageId(), "TASK_REMINDER_" + message.reminderType(), title, content,
                message.taskId(), recipients);
    }

    @Transactional
    public void handleTaskStatus(TaskStatusChangedMessage message) {
        Map<String, Object> task = findTask(message.taskId());
        if (task == null) return;
        List<Long> recipients = assignees(message.taskId(), true);
        String content = String.format("任务[%s] 状态由 %s 变更为 %s",
                task.get("task_no"), message.fromStatus(), message.toStatus());
        insertForRecipients(message.messageId(), "TASK_STATUS_CHANGED", "任务状态已变更", content,
                message.taskId(), recipients);
    }

    public PageResponse<NotificationResponse> page(Long userId, long page, long size, String status) {
        Page<NotificationEntity> result = notificationMapper.selectPage(new Page<>(page, size),
                Wrappers.<NotificationEntity>lambdaQuery()
                        .eq(NotificationEntity::getUserId, userId)
                        .eq(status != null, NotificationEntity::getStatus, status)
                        .orderByDesc(NotificationEntity::getCreatedAt));
        return new PageResponse<>(result.getRecords().stream().map(NotificationResponse::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getUserId, userId)
                .eq(NotificationEntity::getStatus, "UNREAD"));
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        if (notificationMapper.markRead(notificationId, userId) == 0
                && notificationMapper.selectOne(Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getId, notificationId)
                .eq(NotificationEntity::getUserId, userId)) == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId);
    }

    private Map<String, Object> findTask(Long taskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT task_no, title, due_at, status FROM task WHERE id = ? AND deleted = 0", taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Long> assignees(Long taskId, boolean includeCreator) {
        Set<Long> ids = new LinkedHashSet<>();
        if (includeCreator) ids.addAll(jdbcTemplate.queryForList("""
                SELECT t.creator_id FROM task t
                JOIN sys_user u ON u.id = t.creator_id AND u.status = 'ACTIVE' AND u.deleted = 0
                WHERE t.id = ? AND t.deleted = 0
                """, Long.class, taskId));
        ids.addAll(jdbcTemplate.queryForList("""
                SELECT ta.user_id FROM task_assignee ta
                JOIN sys_user u ON u.id = ta.user_id AND u.status = 'ACTIVE' AND u.deleted = 0
                WHERE ta.task_id = ? AND ta.deleted = 0 ORDER BY ta.id
                """, Long.class, taskId));
        return new ArrayList<>(ids);
    }

    private void insertForRecipients(String messageId, String type, String title, String content,
                                     Long taskId, List<Long> recipients) {
        if (messageId == null || messageId.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "消息缺少 messageId");
        }
        for (Long userId : recipients) {
            NotificationEntity notification = new NotificationEntity();
            notification.setSourceMessageId(messageId);
            notification.setUserId(userId);
            notification.setNotificationType(type);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setAggregateType("TASK");
            notification.setAggregateId(taskId);
            if (notificationMapper.insertIdempotent(notification) > 0) {
                NotificationResponse response = NotificationResponse.from(notification);
                if (diagnostics != null) {
                    diagnostics.persisted(response, userId);
                }
                if (eventPublisher != null) {
                    eventPublisher.publishEvent(new NotificationCreatedEvent(userId, response));
                }
            }
        }
    }

    private boolean isTerminal(String status) {
        return List.of("COMPLETED", "CANCELLED", "ARCHIVED").contains(status);
    }
}
