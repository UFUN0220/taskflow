package yvon.backend;

import org.junit.jupiter.api.Test;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.organization.SysDepartmentEntity;
import yvon.backend.organization.SysDepartmentMapper;
import yvon.backend.permission.DataScopeService;
import yvon.backend.permission.DataScopeType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataScopeServiceTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDepartmentMapper departmentMapper = mock(SysDepartmentMapper.class);
    private final DataScopeService service = new DataScopeService(userMapper, departmentMapper);

    @Test
    void departmentAndChildrenScopeExpandsByDepartmentPath() {
        SysUserEntity user = user(7L, 11L);
        SysDepartmentEntity department = new SysDepartmentEntity();
        department.setPath("/engineering/");
        when(userMapper.findDataScopeTypes(7L)).thenReturn(List.of("DEPARTMENT_AND_CHILDREN"));
        when(departmentMapper.findActiveById(11L)).thenReturn(department);
        when(departmentMapper.findActiveIdsByPathPrefix("/engineering/")).thenReturn(List.of(11L, 12L));

        var scope = service.resolve(new UserPrincipal(user, List.of()));

        assertThat(scope.type()).isEqualTo(DataScopeType.DEPARTMENT_AND_CHILDREN);
        assertThat(scope.departmentIds()).containsExactly(11L, 12L);
    }

    @Test
    void broadestActiveRoleScopeWins() {
        when(userMapper.findDataScopeTypes(7L)).thenReturn(List.of("SELF", "DEPARTMENT", "ALL"));

        var scope = service.resolve(new UserPrincipal(user(7L, 11L), List.of()));

        assertThat(scope.type()).isEqualTo(DataScopeType.ALL);
        verifyNoInteractions(departmentMapper);
    }

    private SysUserEntity user(Long id, Long departmentId) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setDepartmentId(departmentId);
        return user;
    }
}
