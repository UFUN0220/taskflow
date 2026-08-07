package yvon.backend.permission;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.organization.SysDepartmentEntity;
import yvon.backend.organization.SysDepartmentMapper;

import java.util.List;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class DataScopeService {

    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;

    public DataScopeService(SysUserMapper userMapper, SysDepartmentMapper departmentMapper) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
    }

    public DataScopeFilter resolve(UserPrincipal principal) {
        DataScopeType type = userMapper.findDataScopeTypes(principal.userId()).stream()
                .map(DataScopeType::valueOf)
                .min((left, right) -> Integer.compare(priority(right), priority(left)))
                .orElse(DataScopeType.SELF);
        if (type == DataScopeType.ALL) {
            return new DataScopeFilter(type, principal.userId(), principal.departmentId(), List.of());
        }
        if (type == DataScopeType.DEPARTMENT_AND_CHILDREN && principal.departmentId() != null) {
            SysDepartmentEntity department = departmentMapper.findActiveById(principal.departmentId());
            if (department != null) {
                return new DataScopeFilter(type, principal.userId(), principal.departmentId(),
                        departmentMapper.findActiveIdsByPathPrefix(department.getPath()));
            }
        }
        if (type == DataScopeType.DEPARTMENT && principal.departmentId() != null) {
            return new DataScopeFilter(type, principal.userId(), principal.departmentId(),
                    List.of(principal.departmentId()));
        }
        if (type == DataScopeType.PROJECT) {
            return new DataScopeFilter(type, principal.userId(), principal.departmentId(), List.of());
        }
        return new DataScopeFilter(DataScopeType.SELF, principal.userId(), principal.departmentId(), List.of());
    }

    private int priority(DataScopeType type) {
        return switch (type) {
            case SELF -> 1;
            case DEPARTMENT -> 2;
            case DEPARTMENT_AND_CHILDREN -> 3;
            case PROJECT -> 4;
            case ALL -> 5;
        };
    }
}
