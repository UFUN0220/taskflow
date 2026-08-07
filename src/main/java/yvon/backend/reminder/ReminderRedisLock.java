package yvon.backend.reminder;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

public class ReminderRedisLock {

    private final StringRedisTemplate redis;
    private final ReminderProperties properties;

    public ReminderRedisLock(StringRedisTemplate redis, ReminderProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String tryAcquire() {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(properties.getLockKey(), token, properties.getLockTtl());
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void release(String token) {
        if (token == null) return;
        redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
                Long.class), List.of(properties.getLockKey()), token);
    }
}
