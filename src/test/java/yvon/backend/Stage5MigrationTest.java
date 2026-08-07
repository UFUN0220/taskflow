package yvon.backend;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Stage5MigrationTest {

    @Test
    void stage5MigrationSeedsProjectAndTaskPermissions() throws Exception {
        String sql = new String(getClass().getResourceAsStream(
                "/db/migration/V5__seed_project_and_task_permissions.sql").readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("'project:read'", "'project:write'", "'project:member:write'");
        assertThat(sql).contains("'task:create'", "'task:submit'", "'task:approve'", "'task:archive'");
        assertThat(sql.toUpperCase()).doesNotContain("DROP TABLE", "DROP DATABASE");
    }
}
