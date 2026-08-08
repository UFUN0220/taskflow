package yvon.backend.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    @Insert("""
            INSERT INTO notification
                (source_message_id, user_id, notification_type, title, content,
                 aggregate_type, aggregate_id, status, version, deleted)
            VALUES (#{sourceMessageId}, #{userId}, #{notificationType}, #{title}, #{content},
                    #{aggregateType}, #{aggregateId}, 'UNREAD', 0, 0)
            ON DUPLICATE KEY UPDATE id = id
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertIdempotent(NotificationEntity notification);

    @Update("""
            UPDATE notification
            SET status = 'READ', read_at = CURRENT_TIMESTAMP(3), version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{notificationId} AND user_id = #{userId}
              AND status = 'UNREAD' AND deleted = 0
            """)
    int markRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId);

    @Update("""
            UPDATE notification
            SET status = 'READ', read_at = CURRENT_TIMESTAMP(3), version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE user_id = #{userId} AND status = 'UNREAD' AND deleted = 0
            """)
    int markAllRead(@Param("userId") Long userId);
}
