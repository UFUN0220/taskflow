package yvon.backend;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.comment.*;
import yvon.backend.task.TaskService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskCommentServiceTest {

    private final TaskCommentMapper commentMapper = mock(TaskCommentMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskCommentService service = new TaskCommentService(commentMapper, userMapper, taskService);

    @Test
    void publicCommentIsStoredAsUserCommentAfterTaskAccessCheck() {
        var principal = TestFixtures.principal();
        doAnswer(invocation -> {
            TaskCommentEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            entity.setCreatedAt(LocalDateTime.now());
            return 1;
        }).when(commentMapper).insert((TaskCommentEntity) any());

        TaskCommentResponse response = service.addUserComment(9L,
                new TaskCommentCreateRequest("  hello task  "), principal);

        verify(taskService).requireVisible(9L, principal);
        verify(commentMapper).insert((TaskCommentEntity) argThat((TaskCommentEntity comment) -> "USER".equals(comment.getCommentType())
                && "hello task".equals(comment.getContent())
                && comment.getAuthorUserId().equals(7L)));
        assertThat(response.commentType()).isEqualTo("USER");
        assertThat(response.content()).isEqualTo("hello task");
    }

    @Test
    void pageBatchLoadsAuthorNamesAndKeepsSystemEventsVisible() {
        var principal = TestFixtures.principal();
        TaskCommentEntity userComment = comment(1L, 7L, "USER", "hello");
        TaskCommentEntity systemComment = comment(2L, 8L, "SYSTEM", "status changed");
        Page<TaskCommentEntity> result = new Page<>(1, 20);
        result.setRecords(List.of(userComment, systemComment));
        result.setTotal(2);
        when(commentMapper.selectPage(any(Page.class), any())).thenReturn(result);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(7L, "Alice"), user(8L, "Bob")));

        var response = service.page(9L, 1, 20, principal);

        assertThat(response.records()).hasSize(2);
        assertThat(response.records().get(0).authorDisplayName()).isEqualTo("Alice");
        assertThat(response.records().get(1).commentType()).isEqualTo("SYSTEM");
        verify(userMapper).selectBatchIds(List.of(7L, 8L));
    }

    private TaskCommentEntity comment(Long id, Long authorId, String type, String content) {
        TaskCommentEntity comment = new TaskCommentEntity();
        comment.setId(id);
        comment.setTaskId(9L);
        comment.setAuthorUserId(authorId);
        comment.setCommentType(type);
        comment.setContent(content);
        comment.setCreatedAt(LocalDateTime.now());
        return comment;
    }

    private SysUserEntity user(Long id, String name) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setDisplayName(name);
        return user;
    }
}
