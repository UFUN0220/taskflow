package yvon.backend;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Stage6MigrationTest {

    @Test
    void stage6MigrationSeedsDraftMaintenancePermissions() throws Exception {
        String sql = new String(getClass().getResourceAsStream(
                "/db/migration/V6__seed_task_maintenance_permissions.sql").readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("'task:update'", "'task:delete'");
        assertThat(sql).contains("'employee'", "'project_manager'");
        assertThat(sql.toUpperCase()).doesNotContain("DROP TABLE", "DROP DATABASE");
    }
}
