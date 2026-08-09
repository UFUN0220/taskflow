package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import yvon.backend.auth.AuthController;
import yvon.backend.auth.ApiSecurityResponseWriter;
import yvon.backend.auth.AuthSessionService;
import yvon.backend.auth.AuthRateLimiter;
import yvon.backend.auth.JwtTokenService;
import yvon.backend.auth.SysUserMapper;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.bootstrap.SecurityConfig;
import yvon.backend.common.error.GlobalExceptionHandler;
import yvon.backend.common.trace.TraceIdFilter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(controllers = AuthController.class, properties = "taskflow.auth.enabled=true")
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TraceIdFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtTokenService tokenService;

    @MockBean
    private AuthSessionService sessionService;

    @MockBean
    private AuthRateLimiter rateLimiter;

    @MockBean
    private SysUserMapper userMapper;

    @MockBean
    private ApiSecurityResponseWriter responseWriter;

    @Test
    void loginReturnsTokenEnvelope() throws Exception {
        yvon.backend.auth.SysUserEntity user = new yvon.backend.auth.SysUserEntity();
        user.setId(3L);
        user.setUsername("admin");
        user.setEmployeeNo("ADMIN001");
        user.setDisplayName("Admin");
        user.setStatus("ACTIVE");
        UserPrincipal principal = new UserPrincipal(user, List.of(new SimpleGrantedAuthority("auth:me")));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(tokenService.issue(principal)).thenReturn("jwt-test-token");
        when(tokenService.expiresInSeconds()).thenReturn(7200L);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"login\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-test-token"))
                .andExpect(jsonPath("$.data.userId").value(3));

        org.mockito.Mockito.verify(sessionService).register("jwt-test-token");
    }

    @Test
    void badCredentialsReturnUnauthorizedWithoutIssuingToken() throws Exception {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"login\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));

        org.mockito.Mockito.verifyNoInteractions(tokenService);
        org.mockito.Mockito.verify(rateLimiter).recordFailure(any(), org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    @WithMockUser(username = "admin")
    void logoutRevokesThePresentedToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer jwt-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        org.mockito.Mockito.verify(sessionService).revoke("jwt-test-token");
    }
}
