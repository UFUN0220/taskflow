package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Stage8MigrationTest {

    @Test
    void migrationSeedsStableCommentAndAttachmentPermissions() throws IOException {
        String sql = new String(new ClassPathResource("db/migration/V7__seed_comment_attachment_permissions.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("task:comment:read", "task:comment:create",
                "task:attachment:read", "task:attachment:create", "task:attachment:delete")
                .contains("system_admin", "employee", "department_manager", "project_manager")
                .doesNotContain("DROP TABLE");
    }
}
