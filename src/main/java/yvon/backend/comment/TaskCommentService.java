package yvon.backend.comment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.organization.PageResponse;
import yvon.backend.task.TaskEntity;
import yvon.backend.task.TaskService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class TaskCommentService {

    private final TaskCommentMapper commentMapper;
    private final SysUserMapper userMapper;
    private final TaskService taskService;

    public TaskCommentService(TaskCommentMapper commentMapper, SysUserMapper userMapper, TaskService taskService) {
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.taskService = taskService;
    }

    @Transactional
    public TaskCommentResponse addUserComment(Long taskId, TaskCommentCreateRequest request, UserPrincipal principal) {
        taskService.requireVisible(taskId, principal);
        TaskCommentEntity comment = new TaskCommentEntity();
        comment.setTaskId(taskId);
        comment.setAuthorUserId(principal.userId());
        comment.setCommentType("USER");
        comment.setContent(request.content().trim());
        commentMapper.insert(comment);
        return toResponse(comment, principal.displayName());
    }

    /** Reserved for backend-generated task events; the public API only creates USER comments. */
    @Transactional
    public TaskCommentEntity addSystemEvent(Long taskId, Long authorUserId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "系统事件内容不能为空");
        }
        TaskCommentEntity comment = new TaskCommentEntity();
        comment.setTaskId(taskId);
        comment.setAuthorUserId(authorUserId);
        comment.setCommentType("SYSTEM");
        comment.setContent(content.trim());
        commentMapper.insert(comment);
        return comment;
    }

    public PageResponse<TaskCommentResponse> page(Long taskId, long page, long size, UserPrincipal principal) {
        taskService.requireVisible(taskId, principal);
        Page<TaskCommentEntity> result = commentMapper.selectPage(new Page<>(page, size),
                Wrappers.<TaskCommentEntity>lambdaQuery()
                        .eq(TaskCommentEntity::getTaskId, taskId)
                        .orderByDesc(TaskCommentEntity::getCreatedAt));
        List<Long> authorIds = result.getRecords().stream().map(TaskCommentEntity::getAuthorUserId).distinct().toList();
        Map<Long, String> names = new LinkedHashMap<>();
        if (!authorIds.isEmpty()) {
            for (SysUserEntity user : userMapper.selectBatchIds(authorIds)) {
                names.put(user.getId(), user.getDisplayName());
            }
        }
        List<TaskCommentResponse> records = result.getRecords().stream()
                .map(comment -> toResponse(comment, names.get(comment.getAuthorUserId())))
                .toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    private TaskCommentResponse toResponse(TaskCommentEntity comment, String authorDisplayName) {
        return new TaskCommentResponse(comment.getId(), comment.getTaskId(), comment.getAuthorUserId(),
                authorDisplayName, comment.getCommentType(), comment.getContent(), comment.getCreatedAt());
    }
}
