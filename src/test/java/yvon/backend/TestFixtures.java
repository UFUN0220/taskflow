package yvon.backend;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.UserPrincipal;

import java.util.List;

final class TestFixtures {
    private TestFixtures() {}

    static UserPrincipal principal() {
        SysUserEntity user = new SysUserEntity();
        user.setId(7L);
        user.setUsername("tester");
        user.setDisplayName("Tester");
        user.setStatus("ACTIVE");
        return new UserPrincipal(user, List.of(
                new SimpleGrantedAuthority("task:comment:create"),
                new SimpleGrantedAuthority("task:comment:read"),
                new SimpleGrantedAuthority("task:attachment:create"),
                new SimpleGrantedAuthority("task:attachment:read"),
                new SimpleGrantedAuthority("task:attachment:delete")));
    }
}
