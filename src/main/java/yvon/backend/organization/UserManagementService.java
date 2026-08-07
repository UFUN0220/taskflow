package yvon.backend.organization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class UserManagementService {

    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(SysUserMapper userMapper, SysDepartmentMapper departmentMapper,
                                 JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserSummaryResponse create(CreateUserRequest request) {
        if (userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, request.username())) > 0
                || userMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getEmployeeNo, request.employeeNo())) > 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "用户名或工号已存在");
        }
        validateDepartment(request.departmentId());
        SysUserEntity user = new SysUserEntity();
        user.setUsername(request.username());
        user.setEmployeeNo(request.employeeNo());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDepartmentId(request.departmentId());
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        replaceRoles(user.getId(), request.roleCodes() == null || request.roleCodes().isEmpty()
                ? List.of("employee") : request.roleCodes());
        return UserSummaryResponse.from(user);
    }

    @Transactional
    public UserSummaryResponse updateStatus(Long userId, UpdateUserStatusRequest request, UserPrincipal operator) {
        if (operator.userId().equals(userId)) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "不允许修改当前登录用户状态");
        }
        SysUserEntity user = requireUser(userId);
        user.setStatus(request.status());
        user.setVersion(request.version());
        if (userMapper.updateById(user) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "用户已被其他请求修改，请刷新后重试");
        }
        return UserSummaryResponse.from(requireUser(userId));
    }

    @Transactional
    public UserSummaryResponse assignRoles(Long userId, AssignUserRolesRequest request) {
        SysUserEntity user = requireUser(userId);
        user.setVersion(request.version());
        if (userMapper.updateById(user) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "用户已被其他请求修改，请刷新后重试");
        }
        replaceRoles(userId, request.roleCodes());
        return UserSummaryResponse.from(requireUser(userId));
    }

    private void validateDepartment(Long departmentId) {
        if (departmentId != null && departmentMapper.findActiveById(departmentId) == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "部门不存在或已停用");
        }
    }

    private SysUserEntity requireUser(Long userId) {
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void replaceRoles(Long userId, List<String> roleCodes) {
        Set<String> codes = new LinkedHashSet<>(roleCodes);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        for (String code : codes) {
            Long roleId;
            try {
                roleId = jdbcTemplate.queryForObject("""
                        SELECT id FROM sys_role WHERE role_code = ? AND status = 'ACTIVE' AND deleted = 0
                        """, Long.class, code);
            } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
                throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "角色不存在或已停用: " + code);
            }
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, version, deleted) VALUES (?, ?, 0, 0)",
                    userId, roleId);
        }
    }
}
