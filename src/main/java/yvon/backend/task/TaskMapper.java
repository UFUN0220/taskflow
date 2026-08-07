package yvon.backend.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {

    /**
     * Changes the task state only when both the state observed by the caller
     * and its version are still current. The same method is used by transfer
     * with an unchanged target state so assignment changes also consume a
     * version.
     */
    @Update("""
            UPDATE task
            SET status = #{toStatus}, version = version + 1,
                updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{taskId}
              AND status = #{fromStatus}
              AND version = #{version}
              AND deleted = 0
            """)
    int updateStatusWithVersion(@Param("taskId") Long taskId,
                                @Param("fromStatus") String fromStatus,
                                @Param("toStatus") String toStatus,
                                @Param("version") Integer version,
                                @Param("operatorId") Long operatorId);
}
