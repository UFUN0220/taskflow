package yvon.backend;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;
import yvon.backend.auth.AuthSessionService;
import yvon.backend.auth.JwtTokenService;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JwtTokenService tokenService = mock(JwtTokenService.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthSessionService service = new AuthSessionService(redis, tokenService);

    @Test
    void registersSessionWithTokenExpiryTtl() {
        Claims claims = claims();
        when(tokenService.parse("jwt")).thenReturn(claims);
        when(redis.opsForValue()).thenReturn(values);

        service.register("jwt");

        verify(values).set(eq("taskflow:auth:session:jti-1"), eq("3"), any());
    }

    @Test
    void activeSessionIsCheckedByJti() {
        Claims claims = claims();
        when(redis.hasKey("taskflow:auth:session:jti-1")).thenReturn(true);

        assertThat(service.isActive(claims)).isTrue();
    }

    @Test
    void redisFailureFailsClosedForSessionAuthentication() {
        when(redis.hasKey("taskflow:auth:session:jti-1"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThat(service.isActive(claims())).isFalse();
    }

    @Test
    void logoutDeletesOnlyPresentedSession() {
        when(tokenService.parse("jwt")).thenReturn(claims());

        service.revoke("jwt");

        verify(redis).delete("taskflow:auth:session:jti-1");
    }

    private Claims claims() {
        return Jwts.claims()
                .id("jti-1")
                .add("uid", 3L)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .build();
    }
}
