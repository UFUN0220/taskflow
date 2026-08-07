package yvon.backend;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.organization.SysDepartmentMapper;
import yvon.backend.permission.DataScopeFilter;
import yvon.backend.permission.DataScopeService;
import yvon.backend.permission.DataScopeType;
import yvon.backend.project.ProjectService;
import yvon.backend.reminder.ReminderPlanService;
import yvon.backend.task.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskServiceDraftTest {

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDepartmentMapper departmentMapper = mock(SysDepartmentMapper.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final DataScopeService dataScopeService = mock(DataScopeService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ReminderPlanService reminderPlanService = mock(ReminderPlanService.class);
    private final TaskService service = new TaskService(taskMapper, userMapper, departmentMapper,
            projectService, dataScopeService, jdbcTemplate, reminderPlanService);

    @Test
    void onlyDraftCanBeEditedAndDeleteUsesVersion() {
        TaskEntity task = task(1L, "DRAFT");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);
        when(dataScopeService.resolve(any(UserPrincipal.class)))
                .thenReturn(new DataScopeFilter(DataScopeType.ALL, 7L, null, List.of()));

        TaskResponse response = service.updateDraft(1L,
                new TaskUpdateRequest("new title", "updated", "HIGH", null, 0), principal("task:update"));

        assertThat(response.title()).isEqualTo("new title");
        assertThat(task.getTitle()).isEqualTo("new title");

        service.deleteDraft(1L, new TaskTransitionRequest(1), principal("task:delete"));

        assertThat(task.getDeleted()).isEqualTo(1);
        verify(taskMapper, times(2)).updateById(any(TaskEntity.class));
    }

    @Test
    void pageLoadsAssigneesWithOneBatchQueryForAllRecords() {
        TaskEntity first = task(1L, "DRAFT");
        TaskEntity second = task(2L, "DRAFT");
        Page<TaskEntity> result = new Page<>(1, 20);
        result.setRecords(List.of(first, second));
        result.setTotal(2);
        when(taskMapper.selectPage(any(Page.class), any())).thenReturn(result);
        when(dataScopeService.resolve(any(UserPrincipal.class)))
                .thenReturn(new DataScopeFilter(DataScopeType.ALL, 7L, null, List.of()));

        var response = service.page(new TaskPageQuery(1, 20, null, null, null, null, null,
                null, null, null, null, null, null), principal());

        assertThat(response.records()).hasSize(2);
        verify(jdbcTemplate, times(1)).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), any(Object[].class));
    }

    private TaskEntity task(Long id, String status) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setVersion(0);
        task.setCreatorId(7L);
        task.setStatus(status);
        task.setTaskNo("TASK-" + id);
        task.setTitle("old title");
        task.setPriority("MEDIUM");
        return task;
    }

    private UserPrincipal principal(String... authorities) {
        SysUserEntity user = new SysUserEntity();
        user.setId(7L);
        user.setUsername("tester");
        user.setDisplayName("Tester");
        user.setStatus("ACTIVE");
        return new UserPrincipal(user, java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList());
    }
}
