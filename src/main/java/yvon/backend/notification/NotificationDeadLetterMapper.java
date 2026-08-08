package yvon.backend.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationDeadLetterMapper extends BaseMapper<NotificationDeadLetterEntity> {

    @Insert("""
            INSERT INTO notification_dead_letter
                (message_id, event_type, trace_id, payload_json, plan_id, task_id,
                 error_reason, retry_count, status, last_failed_at, version, deleted)
            VALUES (#{messageId}, #{eventType}, #{traceId}, #{payloadJson}, #{planId}, #{taskId},
                    #{errorReason}, #{retryCount}, #{status}, CURRENT_TIMESTAMP(3), 0, 0)
            ON DUPLICATE KEY UPDATE
                event_type = VALUES(event_type), trace_id = VALUES(trace_id),
                payload_json = VALUES(payload_json), plan_id = VALUES(plan_id), task_id = VALUES(task_id),
                error_reason = VALUES(error_reason), retry_count = VALUES(retry_count),
                status = VALUES(status), last_failed_at = CURRENT_TIMESTAMP(3),
                replayed_at = NULL, version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            """)
    int recordFailure(NotificationDeadLetterEntity entity);

    @Update("""
            UPDATE notification_dead_letter
            SET status = 'REPLAYED', replayed_at = CURRENT_TIMESTAMP(3),
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'DEAD' AND deleted = 0
            """)
    int markReplayed(@Param("id") Long id);
}
