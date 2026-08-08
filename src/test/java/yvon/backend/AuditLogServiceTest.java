package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import yvon.backend.audit.AuditLogService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AuditLogService service = new AuditLogService(jdbcTemplate);

    @Test
    void persistsOnlyTheStructuredAuditColumns() {
        service.record("trace-1", 7L, "TASK", "42", "SUBMIT", "SUCCESS",
                "POST", "/api/tasks/42/submit", "127.0.0.1", "{\"durationMs\":1}");

        verify(jdbcTemplate).update(anyString(), eq("trace-1"), eq(7L), eq("TASK"), eq("42"),
                eq("SUBMIT"), eq("SUCCESS"), eq("POST"), eq("/api/tasks/42/submit"),
                eq("127.0.0.1"), eq("{\"durationMs\":1}"));
    }
}
