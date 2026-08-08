package yvon.backend.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import yvon.backend.organization.PageResponse;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "taskflow.audit.enabled", havingValue = "true")
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String traceId, Long operatorId, String resourceType, String resourceId,
                       String action, String result, String requestMethod, String requestUri,
                       String ipAddress, String detailJson) {
        jdbcTemplate.update("""
                INSERT INTO audit_log
                    (trace_id, operator_id, resource_type, resource_id, action, result,
                     request_method, request_uri, ip_address, detail_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, traceId, operatorId, resourceType, resourceId, action, result,
                requestMethod, requestUri, ipAddress, detailJson);
    }

    public PageResponse<AuditLogResponse> page(long page, long size, String traceId,
                                                String resourceType, String resourceId) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        appendFilter(where, parameters, "trace_id", traceId);
        appendFilter(where, parameters, "resource_type", resourceType);
        appendFilter(where, parameters, "resource_id", resourceId);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log" + where, Long.class,
                parameters.toArray());
        long offset = (page - 1) * size;
        List<Object> dataParameters = new ArrayList<>(parameters);
        dataParameters.add(offset);
        dataParameters.add(size);
        List<AuditLogResponse> records = jdbcTemplate.query("""
                SELECT id, trace_id, operator_id, resource_type, resource_id, action, result,
                       request_method, request_uri, ip_address, detail_json, occurred_at
                FROM audit_log
                """ + where + " ORDER BY occurred_at DESC, id DESC LIMIT ?, ?", dataParameters.toArray(),
                (rs, rowNum) -> new AuditLogResponse(
                        rs.getLong("id"),
                        rs.getString("trace_id"),
                        nullableLong(rs.getLong("operator_id"), rs.wasNull()),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        rs.getString("action"),
                        rs.getString("result"),
                        rs.getString("request_method"),
                        rs.getString("request_uri"),
                        rs.getString("ip_address"),
                        rs.getString("detail_json"),
                        localDateTime(rs.getTimestamp("occurred_at"))));
        long pages = total == null || total == 0 ? 0 : (total + size - 1) / size;
        return new PageResponse<>(records, total == null ? 0 : total, page, size, pages);
    }

    private void appendFilter(StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            parameters.add(value.trim());
        }
    }

    private static Long nullableLong(long value, boolean wasNull) {
        return wasNull ? null : value;
    }

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
