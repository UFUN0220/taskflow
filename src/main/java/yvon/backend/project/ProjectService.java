package yvon.backend.project;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.organization.SysDepartmentMapper;
import yvon.backend.permission.DataScopeFilter;
import yvon.backend.permission.DataScopeService;
import yvon.backend.permission.DataScopeType;

import java.util.List;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectService {

    private final SysProjectMapper projectMapper;
    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbcTemplate;

    public ProjectService(SysProjectMapper projectMapper, SysDepartmentMapper departmentMapper,
                          SysUserMapper userMapper, DataScopeService dataScopeService, JdbcTemplate jdbcTemplate) {
        this.projectMapper = projectMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.dataScopeService = dataScopeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ProjectResponse create(ProjectCreateRequest request, UserPrincipal principal) {
        if (request.endAt() != null && request.startAt() != null && request.endAt().isBefore(request.startAt())) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "项目结束时间不能早于开始时间");
        }
        if (projectMapper.selectCount(Wrappers.<SysProjectEntity>lambdaQuery()
                .eq(SysProjectEntity::getProjectCode, request.projectCode())) > 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "项目编码已存在");
        }
        if (request.departmentId() != null && departmentMapper.findActiveById(request.departmentId()) == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "项目部门不存在或已停用");
        }
        SysProjectEntity project = new SysProjectEntity();
        project.setProjectCode(request.projectCode());
        project.setProjectName(request.projectName());
        project.setDepartmentId(request.departmentId());
        project.setOwnerUserId(principal.userId());
        project.setStatus("ACTIVE");
        project.setStartAt(request.startAt());
        project.setEndAt(request.endAt());
        projectMapper.insert(project);
        jdbcTemplate.update("INSERT INTO sys_project_member (project_id, user_id, member_role, version, deleted) VALUES (?, ?, 'MANAGER', 0, 0)",
                project.getId(), principal.userId());
        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> list(UserPrincipal principal) {
        DataScopeFilter scope = dataScopeService.resolve(principal);
        List<Long> visibleIds = visibleProjectIds(principal, scope);
        var query = Wrappers.<SysProjectEntity>lambdaQuery()
                .eq(SysProjectEntity::getStatus, "ACTIVE")
                .orderByDesc(SysProjectEntity::getId);
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                query.eq(SysProjectEntity::getId, -1L);
            } else {
                query.in(SysProjectEntity::getId, visibleIds);
            }
        }
        return projectMapper.selectList(query).stream().map(ProjectResponse::from).toList();
    }

    @Transactional
    public void addMember(Long projectId, ProjectMemberRequest request, UserPrincipal principal) {
        SysProjectEntity project = requireVisible(projectId, principal);
        if (!isManager(projectId, principal.userId())) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "只有项目负责人或项目经理可以维护成员");
        }
        if (userMapper.selectById(request.userId()) == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "项目成员不存在");
        }
        jdbcTemplate.update("""
                INSERT INTO sys_project_member (project_id, user_id, member_role, version, deleted)
                VALUES (?, ?, ?, 0, 0)
                ON DUPLICATE KEY UPDATE member_role = ?, deleted = 0
                """, project.getId(), request.userId(), request.memberRole(), request.memberRole());
    }

    public SysProjectEntity requireVisible(Long projectId, UserPrincipal principal) {
        SysProjectEntity project = projectMapper.selectById(projectId);
        if (project == null || !"ACTIVE".equals(project.getStatus())) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "项目不存在或已停用");
        }
        List<Long> visible = visibleProjectIds(principal, dataScopeService.resolve(principal));
        if (visible != null && !visible.contains(projectId)) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "无权访问该项目");
        }
        return project;
    }

    public boolean isManager(Long projectId, Long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_project p
                LEFT JOIN sys_project_member pm ON pm.project_id = p.id AND pm.user_id = ? AND pm.deleted = 0
                WHERE p.id = ? AND (p.owner_user_id = ? OR pm.member_role = 'MANAGER')
                """, Integer.class, userId, projectId, userId);
        return count != null && count > 0;
    }

    private List<Long> visibleProjectIds(UserPrincipal principal, DataScopeFilter scope) {
        if (scope.type() == DataScopeType.ALL) {
            return null;
        }
        if (scope.type() == DataScopeType.DEPARTMENT || scope.type() == DataScopeType.DEPARTMENT_AND_CHILDREN) {
            if (scope.departmentIds().isEmpty()) return List.of();
            String placeholders = String.join(",", scope.departmentIds().stream().map(value -> "?").toList());
            return jdbcTemplate.queryForList("SELECT id FROM sys_project WHERE status = 'ACTIVE' AND department_id IN (" + placeholders + ")",
                    Long.class, scope.departmentIds().toArray());
        }
        if (scope.type() == DataScopeType.PROJECT) {
            return jdbcTemplate.queryForList("""
                    SELECT DISTINCT p.id FROM sys_project p
                    LEFT JOIN sys_project_member pm ON pm.project_id = p.id AND pm.deleted = 0
                    WHERE p.status = 'ACTIVE' AND (p.owner_user_id = ? OR pm.user_id = ?)
                    """, Long.class, principal.userId(), principal.userId());
        }
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT p.id FROM sys_project p
                LEFT JOIN sys_project_member pm ON pm.project_id = p.id AND pm.deleted = 0
                WHERE p.status = 'ACTIVE' AND (p.owner_user_id = ? OR pm.user_id = ?)
                """, Long.class, principal.userId(), principal.userId());
    }
}
