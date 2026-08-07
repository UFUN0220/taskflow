package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationStructureTest {

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "sys_user", "sys_department", "sys_position", "sys_user_position",
            "sys_role", "sys_permission", "sys_user_role", "sys_role_permission",
            "sys_role_data_scope", "sys_project", "sys_project_member", "task", "task_assignee", "task_comment",
            "task_attachment", "task_operation_log", "notification", "reminder_plan",
            "audit_log"
    );

    @Test
    void v1MigrationContainsRequiredTablesAndConstraints() throws IOException {
        String sql = new String(
                new ClassPathResource("db/migration/V1__init_schema.sql")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );

        for (String table : REQUIRED_TABLES) {
            assertTrue(sql.contains("CREATE TABLE `" + table + "`"),
                    () -> "Missing required table: " + table);
        }

        assertTrue(sql.contains("CONSTRAINT `uk_user_username` UNIQUE (`username`)"));
        assertTrue(sql.contains("CONSTRAINT `uk_user_employee_no` UNIQUE (`employee_no`)"));
        assertTrue(sql.contains("CONSTRAINT `chk_task_status` CHECK"));
        assertTrue(sql.contains("`version` INT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("CONSTRAINT `uk_task_assignee_user` UNIQUE (`task_id`, `user_id`)"));
        assertTrue(sql.contains("CONSTRAINT `uk_reminder_task_type_trigger` UNIQUE"));
        assertTrue(sql.contains("CREATE INDEX `idx_task_status_due`"));
        assertTrue(sql.contains("CREATE INDEX `idx_notification_user_status_created`"));
        assertFalse(sql.contains("DROP TABLE"), "Initial migration must not delete existing tables");
    }

    @Test
    void v2MigrationDefinesBaseAuditAndLogicalDeleteFields() throws IOException {
        String sql = new String(
                new ClassPathResource("db/migration/V2__add_base_audit_fields.sql")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("ADD COLUMN `created_by` BIGINT NULL"));
        assertTrue(sql.contains("ADD COLUMN `updated_by` BIGINT NULL"));
        assertTrue(sql.contains("ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0"));
        assertFalse(sql.contains("DROP TABLE"), "Audit migration must not delete tables");
    }

    @Test
    void v3MigrationSeedsStablePermissionCodesAndBuiltInRoles() throws IOException {
        String sql = new String(
                new ClassPathResource("db/migration/V3__seed_auth_permissions.sql")
                        .getInputStream()
                        .readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(sql.contains("auth:me"));
        assertTrue(sql.contains("user:read"));
        assertTrue(sql.contains("department:read"));
        assertTrue(sql.contains("system_admin"));
        assertTrue(sql.contains("employee"));
        assertFalse(sql.toLowerCase().contains("password"),
                "Permission seed must not contain plaintext passwords");
    }
}
