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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskServiceStateConcurrencyTest {

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
    void transitionUsesObservedStatusAndVersionAndWritesLogAfterSuccessfulUpdate() {
        TaskEntity task = task(1L, "DRAFT", 4);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateStatusWithVersion(1L, "DRAFT", "PENDING_ACCEPTANCE", 4, 7L)).thenReturn(1);
        when(dataScopeService.resolve(any(UserPrincipal.class)))
                .thenReturn(new DataScopeFilter(DataScopeType.ALL, 7L, null, List.of()));

        service.transition(1L, TaskCommand.SUBMIT, new TaskTransitionRequest(4), principal());

        verify(taskMapper).updateStatusWithVersion(1L, "DRAFT", "PENDING_ACCEPTANCE", 4, 7L);
        verify(taskMapper, never()).updateById(any(TaskEntity.class));
        verify(jdbcTemplate).update(contains("INSERT INTO task_operation_log"),
                eq(1L), eq(7L), eq("SUBMIT"), eq("DRAFT"), eq("PENDING_ACCEPTANCE"),
                any(), any(), isNull(), any());
    }

    @Test
    void staleOrConcurrentCommandDoesNotWriteAnOrphanOperationLog() {
        TaskEntity task = task(1L, "DRAFT", 4);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateStatusWithVersion(anyLong(), anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(0);
        when(dataScopeService.resolve(any(UserPrincipal.class)))
                .thenReturn(new DataScopeFilter(DataScopeType.ALL, 7L, null, List.of()));

        assertThatThrownBy(() -> service.transition(1L, TaskCommand.SUBMIT,
                new TaskTransitionRequest(4), principal()))
                .isInstanceOf(yvon.backend.common.error.BusinessException.class)
                .hasMessageContaining("重复/并发命令");

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void twoConcurrentCommandsHaveExactlyOneSuccessfulConditionalUpdate() throws Exception {
        TaskEntity task = task(1L, "DRAFT", 4);
        AtomicBoolean winner = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.updateStatusWithVersion(1L, "DRAFT", "PENDING_ACCEPTANCE", 4, 7L))
                .thenAnswer(invocation -> winner.compareAndSet(false, true) ? 1 : 0);
        when(dataScopeService.resolve(any(UserPrincipal.class)))
                .thenReturn(new DataScopeFilter(DataScopeType.ALL, 7L, null, List.of()));

        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> executeAfter(start));
            Future<Boolean> second = executor.submit(() -> executeAfter(start));
            start.countDown();

            assertThat(java.util.stream.Stream.of(first.get(), second.get()).filter(Boolean::booleanValue).count())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean executeAfter(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.transition(1L, TaskCommand.SUBMIT, new TaskTransitionRequest(4), principal());
            return true;
        } catch (yvon.backend.common.error.BusinessException exception) {
            return false;
        }
    }

    @Test
    void stateMachineRejectsIllegalAndTerminalCommands() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> TaskStateMachine.target(TaskStatus.DRAFT, TaskCommand.APPROVE))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> TaskStateMachine.target(TaskStatus.ARCHIVED, TaskCommand.START))
                .isInstanceOf(IllegalStateException.class);
    }

    private TaskEntity task(Long id, String status, int version) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setVersion(version);
        task.setCreatorId(7L);
        task.setStatus(status);
        task.setTaskNo("TASK-" + id);
        task.setTitle("state task");
        task.setPriority("MEDIUM");
        return task;
    }

    private UserPrincipal principal() {
        SysUserEntity user = new SysUserEntity();
        user.setId(7L);
        user.setUsername("tester");
        user.setDisplayName("Tester");
        user.setStatus("ACTIVE");
        return new UserPrincipal(user, List.of(new SimpleGrantedAuthority("task:submit")));
    }
}
