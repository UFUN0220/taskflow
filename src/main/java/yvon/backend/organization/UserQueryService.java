package yvon.backend.organization;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.permission.DataScopeFilter;
import yvon.backend.permission.DataScopeService;
import yvon.backend.permission.DataScopeType;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
class UserQueryService {

    private final SysUserMapper userMapper;
    private final DataScopeService dataScopeService;

    UserQueryService(SysUserMapper userMapper, DataScopeService dataScopeService) {
        this.userMapper = userMapper;
        this.dataScopeService = dataScopeService;
    }

    PageResponse<UserSummaryResponse> page(UserPageQuery query, UserPrincipal principal) {
        Page<SysUserEntity> page = new Page<>(query.page(), query.size());
        var wrapper = Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getStatus, "ACTIVE")
                .orderByAsc(SysUserEntity::getId);
        DataScopeFilter scope = dataScopeService.resolve(principal);
        if (scope.type() == DataScopeType.ALL && query.departmentId() != null) {
            wrapper.eq(SysUserEntity::getDepartmentId, query.departmentId());
        } else if (scope.type() == DataScopeType.DEPARTMENT || scope.type() == DataScopeType.DEPARTMENT_AND_CHILDREN) {
            if (!scope.allowsDepartment(query.departmentId())) {
                wrapper.eq(SysUserEntity::getId, -1L);
            } else if (query.departmentId() != null) {
                wrapper.eq(SysUserEntity::getDepartmentId, query.departmentId());
            } else {
                wrapper.in(SysUserEntity::getDepartmentId, scope.departmentIds());
            }
        } else {
            wrapper.eq(SysUserEntity::getId, principal.userId());
        }
        Page<SysUserEntity> result = userMapper.selectPage(page, wrapper);
        return new PageResponse<>(result.getRecords().stream().map(UserSummaryResponse::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }
}
