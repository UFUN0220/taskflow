package yvon.backend.task;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.common.trace.TraceIdContext;
import yvon.backend.organization.PageResponse;
import yvon.backend.organization.SysDepartmentMapper;
import yvon.backend.permission.DataScopeFilter;
import yvon.backend.permission.DataScopeService;
import yvon.backend.permission.DataScopeType;
import yvon.backend.project.ProjectService;
import yvon.backend.reminder.ReminderPlanService;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class TaskService {

    private final TaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final ProjectService projectService;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbcTemplate;
    private final ReminderPlanService reminderPlanService;

    public TaskService(TaskMapper taskMapper, SysUserMapper userMapper, SysDepartmentMapper departmentMapper,
                       ProjectService projectService, DataScopeService dataScopeService, JdbcTemplate jdbcTemplate,
                       ReminderPlanService reminderPlanService) {
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.projectService = projectService;
        this.dataScopeService = dataScopeService;
        this.jdbcTemplate = jdbcTemplate;
        this.reminderPlanService = reminderPlanService;
    }

    @Transactional
    public TaskResponse create(TaskCreateRequest request, UserPrincipal principal) {
        if (taskMapper.selectCount(Wrappers.<TaskEntity>lambdaQuery()
                .eq(TaskEntity::getTaskNo, request.taskNo())) > 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "任务编号已存在");
        }
        var project = request.projectId() == null ? null : projectService.requireVisible(request.projectId(), principal);
        Long departmentId = request.departmentId();
        if (departmentId != null && departmentMapper.findActiveById(departmentId) == null) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "任务部门不存在或已停用");
        }
        if (departmentId == null) {
            departmentId = project != null ? project.getDepartmentId() : principal.departmentId();
        }
        Long primaryAssigneeId = request.primaryAssigneeId() == null ? principal.userId() : request.primaryAssigneeId();
        validateAssignee(primaryAssigneeId, project == null ? null : project.getId());
        Set<Long> collaborators = new LinkedHashSet<>(request.collaboratorIds() == null
                ? List.of() : request.collaboratorIds());
        collaborators.remove(primaryAssigneeId);
        collaborators.forEach(userId -> validateAssignee(userId, project == null ? null : project.getId()));

        TaskEntity task = new TaskEntity();
        task.setTaskNo(request.taskNo());
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setProjectId(request.projectId());
        task.setDepartmentId(departmentId);
        task.setCreatorId(principal.userId());
        task.setStatus(TaskStatus.DRAFT.name());
        task.setPriority(request.priority());
        task.setDueAt(request.dueAt());
        taskMapper.insert(task);
        insertAssignee(task.getId(), primaryAssigneeId, "PRIMARY");
        for (Long collaborator : collaborators) insertAssignee(task.getId(), collaborator, "COLLABORATOR");
        writeOperationLog(task, principal.userId(), "CREATE", TaskStatus.DRAFT.name(), TaskStatus.DRAFT.name(),
                "创建任务", 0, 0);
        reminderPlanService.syncForTask(task);
        return toResponse(taskMapper.selectById(task.getId()));
    }

    @Transactional
    public TaskResponse updateDraft(Long taskId, TaskUpdateRequest request, UserPrincipal principal) {
        TaskEntity task = requireVisible(taskId, principal);
        if (!TaskStatus.DRAFT.name().equals(task.getStatus())) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "只有草稿任务可以编辑");
        }
        if (!task.getCreatorId().equals(principal.userId()) && !hasAuthority(principal, "task:update")) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "只有创建人或授权用户可以编辑任务草稿");
        }
        LocalDateTime previousDueAt = task.getDueAt();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority());
        task.setDueAt(request.dueAt());
        task.setVersion(request.version());
        if (taskMapper.updateById(task) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "任务已被其他请求修改，请刷新后重试");
        }
        writeOperationLog(task, principal.userId(), "UPDATE", TaskStatus.DRAFT.name(), TaskStatus.DRAFT.name(),
                "编辑任务草稿", request.version(), request.version() + 1);
        if (!java.util.Objects.equals(previousDueAt, task.getDueAt())) {
            reminderPlanService.syncForTask(task);
        }
        return toResponse(taskMapper.selectById(taskId));
    }

    @Transactional
    public void deleteDraft(Long taskId, TaskTransitionRequest request, UserPrincipal principal) {
        TaskEntity task = requireVisible(taskId, principal);
        if (!TaskStatus.DRAFT.name().equals(task.getStatus())) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "已进入流程的任务不能删除");
        }
        if (!task.getCreatorId().equals(principal.userId()) && !hasAuthority(principal, "task:delete")) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "只有创建人或授权用户可以删除任务草稿");
        }
        task.setDeleted(1);
        task.setVersion(request.version());
        if (taskMapper.updateById(task) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, "任务已被其他请求修改，请刷新后重试");
        }
        writeOperationLog(task, principal.userId(), "DELETE", TaskStatus.DRAFT.name(), TaskStatus.DRAFT.name(),
                "删除任务草稿", request.version(), request.version() + 1);
        reminderPlanService.cancelForTask(taskId);
    }

    public PageResponse<TaskResponse> page(TaskPageQuery query, UserPrincipal principal) {
        DataScopeFilter scope = dataScopeService.resolve(principal);
        List<Long> visibleIds = visibleTaskIds(principal, scope);
        Page<TaskEntity> page = new Page<>(query.page(), query.size());
        var wrapper = Wrappers.<TaskEntity>lambdaQuery().orderByDesc(TaskEntity::getId);
        if (query.title() != null && !query.title().isBlank()) wrapper.like(TaskEntity::getTitle, query.title());
        if (query.status() != null) wrapper.eq(TaskEntity::getStatus, query.status());
        if (query.priority() != null) wrapper.eq(TaskEntity::getPriority, query.priority());
        if (query.creatorId() != null) wrapper.eq(TaskEntity::getCreatorId, query.creatorId());
        if (query.departmentId() != null) wrapper.eq(TaskEntity::getDepartmentId, query.departmentId());
        if (query.projectId() != null) wrapper.eq(TaskEntity::getProjectId, query.projectId());
        if (query.dueFrom() != null) wrapper.ge(TaskEntity::getDueAt, query.dueFrom());
        if (query.dueTo() != null) wrapper.le(TaskEntity::getDueAt, query.dueTo());
        if (query.createdFrom() != null) wrapper.ge(TaskEntity::getCreatedAt, query.createdFrom());
        if (query.createdTo() != null) wrapper.le(TaskEntity::getCreatedAt, query.createdTo());
        if (query.assigneeId() != null) {
            List<Long> assignedTaskIds = jdbcTemplate.queryForList(
                    "SELECT task_id FROM task_assignee WHERE user_id = ? AND deleted = 0", Long.class, query.assigneeId());
            if (assignedTaskIds.isEmpty()) wrapper.eq(TaskEntity::getId, -1L);
            else wrapper.in(TaskEntity::getId, assignedTaskIds);
        }
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) wrapper.eq(TaskEntity::getId, -1L);
            else wrapper.in(TaskEntity::getId, visibleIds);
        }
        Page<TaskEntity> result = taskMapper.selectPage(page, wrapper);
        return new PageResponse<>(toResponses(result.getRecords()),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public TaskResponse get(Long taskId, UserPrincipal principal) {
        return toResponse(requireVisible(taskId, principal));
    }

    @Transactional
    public TaskResponse transition(Long taskId, TaskCommand command, TaskTransitionRequest request,
                                   UserPrincipal principal) {
        TaskEntity task = requireVisible(taskId, principal);
        if (!canOperate(task, command, principal)) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "当前用户不能执行该任务命令");
        }
        TaskStatus from = TaskStatus.valueOf(task.getStatus());
        TaskStatus target;
        try {
            target = TaskStateMachine.target(from, command);
        } catch (IllegalStateException exception) {
            throw new BusinessException(BusinessErrorCode.CONFLICT, exception.getMessage());
        }
        if (taskMapper.updateStatusWithVersion(taskId, from.name(), target.name(), request.version(), principal.userId()) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT,
                    "任务版本或当前状态已变化，重复/并发命令被拒绝，请刷新后重试");
        }
        if (command == TaskCommand.ACCEPT) {
            jdbcTemplate.update("UPDATE task_assignee SET accepted_at = CURRENT_TIMESTAMP(3) WHERE task_id = ? AND user_id = ? AND assignee_type = 'PRIMARY'",
                    taskId, principal.userId());
        }
        writeOperationLog(task, principal.userId(), command.name(), from.name(), target.name(), null,
                request.version(), request.version() + 1);
        if (target == TaskStatus.COMPLETED || target == TaskStatus.CANCELLED || target == TaskStatus.ARCHIVED) {
            reminderPlanService.cancelForTask(taskId);
        }
        return toResponse(taskMapper.selectById(taskId));
    }

    @Transactional
    public TaskResponse transfer(Long taskId, TransferTaskRequest request, UserPrincipal principal) {
        TaskEntity task = requireVisible(taskId, principal);
        if (!canOperate(task, TaskCommand.TRANSFER, principal)) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "当前用户不能转交该任务");
        }
        validateAssignee(request.primaryAssigneeId(), task.getProjectId());
        Set<Long> collaborators = new LinkedHashSet<>(request.collaboratorIds() == null
                ? List.of() : request.collaboratorIds());
        collaborators.remove(request.primaryAssigneeId());
        collaborators.forEach(userId -> validateAssignee(userId, task.getProjectId()));
        if (taskMapper.updateStatusWithVersion(taskId, task.getStatus(), task.getStatus(), request.version(), principal.userId()) == 0) {
            throw new BusinessException(BusinessErrorCode.CONFLICT,
                    "任务版本或当前状态已变化，重复/并发转交被拒绝，请刷新后重试");
        }
        jdbcTemplate.update("DELETE FROM task_assignee WHERE task_id = ?", taskId);
        insertAssignee(taskId, request.primaryAssigneeId(), "PRIMARY");
        collaborators.forEach(userId -> insertAssignee(taskId, userId, "COLLABORATOR"));
        writeOperationLog(task, principal.userId(), "TRANSFER", task.getStatus(), task.getStatus(),
                "转交主负责人=" + request.primaryAssigneeId(), request.version(), request.version() + 1);
        return toResponse(taskMapper.selectById(taskId));
    }

    public TaskEntity requireVisible(Long taskId, UserPrincipal principal) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        List<Long> visibleIds = visibleTaskIds(principal, dataScopeService.resolve(principal));
        if (visibleIds != null && !visibleIds.contains(taskId)) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "无权访问该任务");
        }
        return task;
    }

    private boolean canOperate(TaskEntity task, TaskCommand command, UserPrincipal principal) {
        if (dataScopeService.resolve(principal).type() == DataScopeType.ALL) return true;
        if (command == TaskCommand.SUBMIT) return task.getCreatorId().equals(principal.userId());
        if (command == TaskCommand.ACCEPT || command == TaskCommand.START || command == TaskCommand.SUBMIT_REVIEW) {
            return isPrimary(task.getId(), principal.userId());
        }
        if (command == TaskCommand.APPROVE || command == TaskCommand.REJECT) {
            return task.getCreatorId().equals(principal.userId())
                    || projectManager(task, principal.userId())
                    || hasAuthority(principal, "task:approve");
        }
        return task.getCreatorId().equals(principal.userId()) || projectManager(task, principal.userId());
    }

    private boolean projectManager(TaskEntity task, Long userId) {
        return task.getProjectId() != null && projectService.isManager(task.getProjectId(), userId);
    }

    private boolean hasAuthority(UserPrincipal principal, String authority) {
        return principal.getAuthorities().stream().anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private boolean isPrimary(Long taskId, Long userId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_assignee WHERE task_id = ? AND user_id = ? AND assignee_type = 'PRIMARY'",
                Integer.class, taskId, userId);
        return count != null && count > 0;
    }

    private void validateAssignee(Long userId, Long projectId) {
        SysUserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "负责人不存在或已停用");
        }
        if (projectId != null) {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM sys_project p
                    LEFT JOIN sys_project_member pm ON pm.project_id = p.id AND pm.user_id = ? AND pm.deleted = 0
                    WHERE p.id = ? AND (p.owner_user_id = ? OR pm.user_id IS NOT NULL)
                    """, Integer.class, userId, projectId, userId);
            if (count == null || count == 0) {
                throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "负责人必须是项目成员");
            }
        }
    }

    private void insertAssignee(Long taskId, Long userId, String type) {
        jdbcTemplate.update("INSERT INTO task_assignee (task_id, user_id, assignee_type, version, deleted) VALUES (?, ?, ?, 0, 0)",
                taskId, userId, type);
    }

    private List<Long> visibleTaskIds(UserPrincipal principal, DataScopeFilter scope) {
        if (scope.type() == DataScopeType.ALL) return null;
        if (scope.type() == DataScopeType.DEPARTMENT || scope.type() == DataScopeType.DEPARTMENT_AND_CHILDREN) {
            if (scope.departmentIds().isEmpty()) return List.of();
            String placeholders = String.join(",", scope.departmentIds().stream().map(value -> "?").toList());
            return jdbcTemplate.queryForList("SELECT id FROM task WHERE department_id IN (" + placeholders + ") AND deleted = 0",
                    Long.class, scope.departmentIds().toArray());
        }
        if (scope.type() == DataScopeType.PROJECT) {
            return jdbcTemplate.queryForList("""
                    SELECT DISTINCT t.id FROM task t
                    JOIN sys_project p ON p.id = t.project_id AND p.deleted = 0
                    LEFT JOIN sys_project_member pm ON pm.project_id = p.id AND pm.user_id = ? AND pm.deleted = 0
                    WHERE t.deleted = 0 AND (p.owner_user_id = ? OR pm.user_id IS NOT NULL)
                    """, Long.class, principal.userId(), principal.userId());
        }
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT t.id FROM task t
                LEFT JOIN task_assignee ta ON ta.task_id = t.id AND ta.deleted = 0
                WHERE t.deleted = 0 AND (t.creator_id = ? OR ta.user_id = ?)
                """, Long.class, principal.userId(), principal.userId());
    }

    private List<TaskResponse> toResponses(List<TaskEntity> tasks) {
        if (tasks.isEmpty()) return List.of();
        Map<Long, List<AssigneeResponse>> assigneesByTask = new LinkedHashMap<>();
        String placeholders = String.join(",", tasks.stream().map(task -> "?").toList());
        Object[] ids = tasks.stream().map(TaskEntity::getId).toArray();
        String sql = "SELECT ta.task_id, ta.user_id, u.display_name, ta.assignee_type, ta.accepted_at "
                + "FROM task_assignee ta JOIN sys_user u ON u.id = ta.user_id "
                + "WHERE ta.task_id IN (" + placeholders + ") AND ta.deleted = 0 "
                + "ORDER BY ta.task_id, CASE ta.assignee_type WHEN 'PRIMARY' THEN 0 ELSE 1 END, ta.id";
        jdbcTemplate.query(sql,
                rs -> {
                    Long taskId = rs.getLong("task_id");
                    assigneesByTask.computeIfAbsent(taskId, ignored -> new java.util.ArrayList<>())
                            .add(new AssigneeResponse(rs.getLong("user_id"), rs.getString("display_name"),
                                    rs.getString("assignee_type"), rs.getTimestamp("accepted_at") == null ? null
                                            : rs.getTimestamp("accepted_at").toLocalDateTime()));
                }, ids);
        return tasks.stream().map(task -> TaskResponse.from(task,
                assigneesByTask.getOrDefault(task.getId(), List.of()))).toList();
    }

    private TaskResponse toResponse(TaskEntity task) {
        if (task == null) throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        return toResponses(List.of(task)).get(0);
    }

    private void writeOperationLog(TaskEntity task, Long operatorId, String operation, String fromStatus,
                                   String toStatus, String note, Integer beforeVersion, Integer afterVersion) {
        String before = fromStatus == null ? null : "{\"status\":\"" + fromStatus + "\",\"version\":" + beforeVersion + "}";
        String after = toStatus == null ? null : "{\"status\":\"" + toStatus + "\",\"version\":" + afterVersion + "}";
        jdbcTemplate.update("""
                INSERT INTO task_operation_log
                    (task_id, operator_id, operation_type, from_status, to_status, before_data, after_data, operation_note, trace_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, task.getId(), operatorId, operation, fromStatus, toStatus, before, after, note,
                TraceIdContext.current().orElse(null));
    }
}
