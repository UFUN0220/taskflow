package yvon.backend.reminder;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReminderProperties.class)
public class ReminderConfiguration {

    @Bean
    Clock reminderClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ReminderRedisLock reminderRedisLock(StringRedisTemplate redis, ReminderProperties properties) {
        return new ReminderRedisLock(redis, properties);
    }
}
