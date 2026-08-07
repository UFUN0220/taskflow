package yvon.backend.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {

    @Select("""
            SELECT * FROM sys_user
            WHERE (username = #{login} OR employee_no = #{login})
              AND status = 'ACTIVE' AND deleted = 0
            LIMIT 1
            """)
    SysUserEntity findActiveByLogin(String login);

    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND r.status = 'ACTIVE' AND r.deleted = 0
              AND ur.deleted = 0
            """)
    List<String> findRoleCodes(Long userId);

    @Select("""
            SELECT p.permission_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            JOIN sys_role_permission rp ON rp.role_id = r.id
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
              AND r.status = 'ACTIVE' AND r.deleted = 0
              AND p.status = 'ACTIVE' AND p.deleted = 0
              AND ur.deleted = 0 AND rp.deleted = 0
            """)
    List<String> findPermissionCodes(Long userId);

    @Select("""
            SELECT DISTINCT rs.scope_type
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            JOIN sys_role_data_scope rs ON rs.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 'ACTIVE' AND r.deleted = 0
              AND rs.deleted = 0
            """)
    List<String> findDataScopeTypes(Long userId);

    @Update("UPDATE sys_user SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = #{userId}")
    int updateLastLogin(Long userId);
}
