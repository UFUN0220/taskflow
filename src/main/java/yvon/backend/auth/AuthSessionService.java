package yvon.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Stores the active JWT session marker in Redis. The JWT remains self-contained,
 * while Redis supplies immediate logout and revocation semantics.
 */
@Component
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionService {

    private static final String KEY_PREFIX = "taskflow:auth:session:";

    private final StringRedisTemplate redis;
    private final JwtTokenService tokenService;

    public AuthSessionService(StringRedisTemplate redis, JwtTokenService tokenService) {
        this.redis = redis;
        this.tokenService = tokenService;
    }

    public void register(String token) {
        Claims claims = tokenService.parse(token);
        String sessionId = sessionId(claims);
        Instant expiresAt = claims.getExpiration().toInstant();
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalStateException("JWT expiration must be in the future");
        }
        Number userId = claims.get("uid", Number.class);
        if (userId == null) {
            throw new IllegalArgumentException("JWT user id is missing");
        }
        redis.opsForValue().set(key(sessionId), String.valueOf(userId), ttl);
    }

    public boolean isActive(Claims claims) {
        return hasSession(sessionId(claims));
    }

    public void revoke(String token) {
        try {
            redis.delete(key(sessionId(tokenService.parse(token))));
        } catch (JwtException | IllegalArgumentException ignored) {
            // Authentication has already rejected malformed tokens; logout is idempotent.
        }
    }

    private boolean hasSession(String sessionId) {
        return Boolean.TRUE.equals(redis.hasKey(key(sessionId)));
    }

    private String sessionId(Claims claims) {
        String id = claims.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("JWT session id is missing");
        }
        return id;
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
