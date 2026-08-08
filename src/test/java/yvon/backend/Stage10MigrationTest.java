package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Stage10MigrationTest {

    @Test
    void migrationAddsNotificationIdempotencyDeadLettersAndPermissions() throws IOException {
        String sql = new String(new ClassPathResource("db/migration/V8__add_notification_delivery_and_permissions.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("source_message_id", "uk_notification_source_message_user",
                        "CREATE TABLE `notification_dead_letter`", "uk_notification_dead_message",
                        "notification:read", "notification:dead-letter:replay")
                .doesNotContain("DROP TABLE");
    }
}
