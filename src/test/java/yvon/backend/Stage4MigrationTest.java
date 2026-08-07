package yvon.backend;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Stage4MigrationTest {

    @Test
    void stage4MigrationSeedsManagementPermissionsAndScopes() throws Exception {
        String sql = new String(getClass().getResourceAsStream(
                "/db/migration/V4__seed_management_permissions_and_scopes.sql").readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("'user:write'", "'user:role:write'", "'department:write'", "'role:write'");
        assertThat(sql).contains("'department_manager'", "'project_manager'");
        assertThat(sql).contains("'DEPARTMENT_AND_CHILDREN'", "'PROJECT'", "'ALL'");
        assertThat(sql.toUpperCase()).doesNotContain("DROP TABLE", "DROP DATABASE");
    }
}
