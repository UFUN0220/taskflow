package yvon.backend.organization;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartmentEntity> {

    @Select("SELECT * FROM sys_department WHERE id = #{id} AND status = 'ACTIVE' AND deleted = 0")
    SysDepartmentEntity findActiveById(Long id);

    @Select("SELECT id FROM sys_department WHERE status = 'ACTIVE' AND deleted = 0 AND path LIKE CONCAT(#{pathPrefix}, '%')")
    List<Long> findActiveIdsByPathPrefix(String pathPrefix);
}
