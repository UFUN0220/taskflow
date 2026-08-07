package yvon.backend.attachment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskAttachmentMapper extends BaseMapper<TaskAttachmentEntity> {

    @Update("""
            UPDATE task_attachment
            SET status = #{toStatus}, deleted = #{toDeleted}, version = version + 1,
                updated_by = #{operatorId}, updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{attachmentId}
              AND status = #{fromStatus}
              AND version = #{version}
              AND deleted = #{fromDeleted}
            """)
    int updateStatusWithVersion(@Param("attachmentId") Long attachmentId,
                                @Param("fromStatus") String fromStatus,
                                @Param("toStatus") String toStatus,
                                @Param("fromDeleted") Integer fromDeleted,
                                @Param("toDeleted") Integer toDeleted,
                                @Param("version") Integer version,
                                @Param("operatorId") Long operatorId);
}
