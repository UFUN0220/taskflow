package yvon.backend.organization;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import yvon.backend.common.mybatis.AuditEntity;

@TableName("sys_department")
public class SysDepartmentEntity extends AuditEntity {

    @TableField("parent_id")
    private Long parentId;
    @TableField("department_code")
    private String departmentCode;
    @TableField("department_name")
    private String departmentName;
    private String path;
    private Integer level;
    private String status;

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getDepartmentCode() { return departmentCode; }
    public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
