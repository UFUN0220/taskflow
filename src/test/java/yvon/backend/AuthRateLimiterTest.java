package yvon.backend;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import yvon.backend.auth.AuthProperties;
import yvon.backend.auth.AuthRateLimiter;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AuthProperties properties = properties();
    private final AuthRateLimiter limiter = new AuthRateLimiter(redis, properties);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void rejectsWhenFailureWindowAlreadyExists() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenReturn("10");

        assertThatThrownBy(() -> limiter.checkAllowed(request, "Admin"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(BusinessErrorCode.RATE_LIMITED);
    }

    @Test
    void firstFailureStartsBoundedWindow() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(any())).thenReturn(1L);

        limiter.recordFailure(request, "Admin");

        verify(redis).expire(any(), eq(Duration.ofSeconds(60)));
    }

    private AuthProperties properties() {
        AuthProperties result = new AuthProperties();
        result.setLoginRateLimitEnabled(true);
        result.setLoginRateLimitMaxAttempts(10);
        result.setLoginRateLimitWindowSeconds(60);
        return result;
    }
}
