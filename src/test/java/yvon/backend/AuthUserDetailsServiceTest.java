package yvon.backend;

import org.junit.jupiter.api.Test;
import yvon.backend.auth.AuthUserDetailsService;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUserDetailsServiceTest {

    @Test
    void loadsRolesAndStablePermissionAuthorities() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        SysUserEntity user = new SysUserEntity();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmployeeNo("E007");
        user.setDisplayName("Alice");
        user.setPasswordHash("{bcrypt}hash");
        user.setStatus("ACTIVE");
        when(mapper.findActiveByLogin("alice")).thenReturn(user);
        when(mapper.findRoleCodes(7L)).thenReturn(List.of("system_admin"));
        when(mapper.findPermissionCodes(7L)).thenReturn(List.of("user:read", "department:read"));

        UserPrincipal principal = (UserPrincipal) new AuthUserDetailsService(mapper).loadUserByUsername("alice");

        assertThat(principal.userId()).isEqualTo(7L);
        assertThat(principal.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_SYSTEM_ADMIN", "user:read", "department:read");
    }
}
