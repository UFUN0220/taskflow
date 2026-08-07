package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.permission.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RoleServiceTest {

    @Test
    void builtInRoleCannotBeUpdated() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysRoleEntity role = new SysRoleEntity();
        role.setId(1L);
        role.setBuiltIn(true);
        when(roleMapper.selectById(1L)).thenReturn(role);
        RoleService service = new RoleService(roleMapper, permissionMapper, mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.update(1L, new RoleUpdateRequest(
                "系统管理员", "ACTIVE", List.of("user:read"), DataScopeType.ALL, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.FORBIDDEN);
        verifyNoInteractions(permissionMapper);
    }
}
