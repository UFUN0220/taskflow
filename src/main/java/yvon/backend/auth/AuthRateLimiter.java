package yvon.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/** Limits failed login attempts by normalized login and remote address. */
@Component
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthRateLimiter {

    private static final String KEY_PREFIX = "taskflow:auth:login-fail:";

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public AuthRateLimiter(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void checkAllowed(HttpServletRequest request, String login) {
        if (!properties.isLoginRateLimitEnabled()) return;
        String current = redis.opsForValue().get(key(request, login));
        if (current != null && parseCount(current) >= properties.getLoginRateLimitMaxAttempts()) {
            throw new BusinessException(BusinessErrorCode.RATE_LIMITED);
        }
    }

    public void recordFailure(HttpServletRequest request, String login) {
        if (!properties.isLoginRateLimitEnabled()) return;
        String key = key(request, login);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(properties.getLoginRateLimitWindowSeconds()));
        }
    }

    public void recordSuccess(HttpServletRequest request, String login) {
        if (!properties.isLoginRateLimitEnabled()) return;
        redis.delete(key(request, login));
    }

    private String key(HttpServletRequest request, String login) {
        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String normalizedLogin = login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
        return KEY_PREFIX + digest(address + "\n" + normalizedLogin);
    }

    private long parseCount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return properties.getLoginRateLimitMaxAttempts();
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
