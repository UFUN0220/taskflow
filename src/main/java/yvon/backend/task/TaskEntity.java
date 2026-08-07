package yvon.backend.task;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import yvon.backend.common.mybatis.AuditEntity;

import java.time.LocalDateTime;

@TableName("task")
public class TaskEntity extends AuditEntity {
    @TableField("task_no")
    private String taskNo;
    private String title;
    private String description;
    @TableField("project_id")
    private Long projectId;
    @TableField("department_id")
    private Long departmentId;
    @TableField("creator_id")
    private Long creatorId;
    private String status;
    private String priority;
    @TableField("due_at")
    private LocalDateTime dueAt;

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
}
