package yvon.backend.comment;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import yvon.backend.common.mybatis.AuditEntity;

import java.time.LocalDateTime;

@TableName("task_comment")
public class TaskCommentEntity extends AuditEntity {

    @TableField("task_id")
    private Long taskId;
    @TableField("author_user_id")
    private Long authorUserId;
    @TableField("comment_type")
    private String commentType;
    private String content;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }
    public String getCommentType() { return commentType; }
    public void setCommentType(String commentType) { this.commentType = commentType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
