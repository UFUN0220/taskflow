package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import yvon.backend.auth.AuthProperties;
import yvon.backend.auth.JwtTokenService;
import yvon.backend.auth.SysUserEntity;
import yvon.backend.auth.UserPrincipal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void issuesAndParsesUserToken() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("01234567890123456789012345678901");
        properties.setJwtExpirationMinutes(30);
        JwtTokenService tokenService = new JwtTokenService(properties);

        SysUserEntity user = new SysUserEntity();
        user.setId(11L);
        user.setUsername("bob");
        user.setStatus("ACTIVE");
        UserPrincipal principal = new UserPrincipal(user,
                List.of(new SimpleGrantedAuthority("user:read")));

        String token = tokenService.issue(principal);

        assertThat(tokenService.parse(token).getSubject()).isEqualTo("bob");
        assertThat(tokenService.parse(token).get("uid", Number.class).longValue()).isEqualTo(11L);
        assertThat(tokenService.expiresInSeconds()).isEqualTo(1800);
    }
}
