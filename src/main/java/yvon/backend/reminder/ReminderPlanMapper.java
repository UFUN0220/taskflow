package yvon.backend.reminder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReminderPlanMapper extends BaseMapper<ReminderPlanEntity> {

    @Insert("""
            INSERT INTO reminder_plan (task_id, reminder_type, trigger_at, status, version)
            VALUES (#{taskId}, #{reminderType}, #{triggerAt}, 'PLANNED', 0)
            ON DUPLICATE KEY UPDATE
                status = 'PLANNED', last_emitted_at = NULL,
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            """)
    int insertOrReactivate(ReminderPlanEntity plan);

    @Select("""
            SELECT id, task_id, reminder_type, trigger_at, status, last_emitted_at,
                   created_at, updated_at, version
            FROM reminder_plan
            WHERE task_id = #{taskId}
            ORDER BY id
            """)
    List<ReminderPlanEntity> selectByTaskId(@Param("taskId") Long taskId);

    @Select("""
            SELECT id, task_id, reminder_type, trigger_at, status, last_emitted_at,
                   created_at, updated_at, version
            FROM reminder_plan
            WHERE status = 'PLANNED'
            ORDER BY trigger_at, id
            """)
    List<ReminderPlanEntity> selectAllPlanned();

    @Update("""
            UPDATE reminder_plan
            SET status = 'CANCELLED', version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE task_id = #{taskId} AND status = 'PLANNED'
            """)
    int cancelPlanned(@Param("taskId") Long taskId);

    @Update("""
            UPDATE reminder_plan
            SET status = 'EMITTED', last_emitted_at = CURRENT_TIMESTAMP(3),
                version = version + 1, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{planId} AND status = 'PLANNED' AND version = #{version}
            """)
    int markEmitted(@Param("planId") Long planId, @Param("version") Integer version);

    @Update("""
            UPDATE reminder_plan
            SET status = 'FAILED', version = version + 1,
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{planId} AND status = 'PLANNED' AND version = #{version}
            """)
    int markFailed(@Param("planId") Long planId, @Param("version") Integer version);
}
