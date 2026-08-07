package yvon.backend.permission;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final JdbcTemplate jdbcTemplate;

    public RoleService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper, JdbcTemplate jdbcTemplate) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RoleResponse> list() {
        return roleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                        .eq(SysRoleEntity::getStatus, "ACTIVE")
                        .orderByAsc(SysRoleEntity::getId))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        if (roleMapper.selectCount(Wrappers.<SysRoleEntity>lambdaQuery()
                .eq(SysRoleEntity::getRoleCode, request.roleCode())) > 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "角色编码已存在");
        }
        Set<String> permissions = validatePermissions(request.permissionCodes());
        SysRoleEntity role = new SysRoleEntity();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setStatus("ACTIVE");
        role.setBuiltIn(false);
        roleMapper.insert(role);
        replacePermissions(role.getId(), permissions);
        replaceScope(role.getId(), request.scopeType());
        return toResponse(role);
    }

    @Transactional
    public RoleResponse update(Long roleId, RoleUpdateRequest request) {
        SysRoleEntity role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "内置角色不允许修改");
        }
        Set<String> permissions = validatePermissions(request.permissionCodes());
        role.setRoleName(request.roleName());
        role.setStatus(request.status());
        role.setVersion(request.version());
        if (roleMapper.updateById(role) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "角色已被其他请求修改，请刷新后重试");
        }
        replacePermissions(roleId, permissions);
        replaceScope(roleId, request.scopeType());
        return toResponse(requireRole(roleId));
    }

    private SysRoleEntity requireRole(Long roleId) {
        SysRoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private Set<String> validatePermissions(List<String> codes) {
        Set<String> requested = new LinkedHashSet<>(codes);
        Set<String> active = new LinkedHashSet<>(permissionMapper.selectList(
                        Wrappers.<SysPermissionEntity>lambdaQuery().eq(SysPermissionEntity::getStatus, "ACTIVE"))
                .stream().map(SysPermissionEntity::getPermissionCode).toList());
        if (!active.containsAll(requested)) {
            requested.removeAll(active);
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "存在无效权限编码: " + requested);
        }
        return requested;
    }

    private void replacePermissions(Long roleId, Set<String> codes) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        for (String code : codes) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission (role_id, permission_id)
                    SELECT ?, id FROM sys_permission WHERE permission_code = ? AND status = 'ACTIVE' AND deleted = 0
                    """, roleId, code);
        }
    }

    private void replaceScope(Long roleId, DataScopeType type) {
        jdbcTemplate.update("DELETE FROM sys_role_data_scope WHERE role_id = ?", roleId);
        jdbcTemplate.update("INSERT INTO sys_role_data_scope (role_id, scope_type, version, deleted) VALUES (?, ?, 0, 0)",
                roleId, type.name());
    }

    private RoleResponse toResponse(SysRoleEntity role) {
        List<String> permissionCodes = jdbcTemplate.queryForList("""
                SELECT p.permission_code FROM sys_role_permission rp
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE rp.role_id = ? AND p.status = 'ACTIVE' AND p.deleted = 0
                ORDER BY p.id
                """, String.class, role.getId());
        List<String> scopes = jdbcTemplate.queryForList(
                "SELECT scope_type FROM sys_role_data_scope WHERE role_id = ? AND deleted = 0", String.class, role.getId());
        DataScopeType scope = scopes.isEmpty() ? null : DataScopeType.valueOf(scopes.get(0));
        return new RoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.getStatus(),
                role.getBuiltIn(), role.getVersion(), permissionCodes, scope);
    }
}
