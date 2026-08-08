package yvon.backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.trace.TraceIdContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
@ConditionalOnProperty(name = "taskflow.audit.enabled", havingValue = "true")
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("/(\\d+)(?:/|$)");

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditAction)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            writeAudit(auditAction, "SUCCESS", startedAt, null);
            return result;
        } catch (Throwable exception) {
            writeAudit(auditAction, "FAILURE", startedAt, exception);
            throw exception;
        }
    }

    private void writeAudit(AuditAction action, String result, long startedAt, Throwable exception) {
        try {
            HttpServletRequest request = currentRequest();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long operatorId = authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                    ? principal.userId() : null;
            String uri = request == null ? null : request.getRequestURI();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("durationMs", (System.nanoTime() - startedAt) / 1_000_000L);
            detail.put("method", request == null ? null : request.getMethod());
            if (exception != null) detail.put("exception", exception.getClass().getSimpleName());
            String detailJson = objectMapper.writeValueAsString(detail);
            auditLogService.record(
                    TraceIdContext.getOrCreate(), operatorId, action.resourceType(), resourceId(uri), action.action(),
                    result, request == null ? null : request.getMethod(), uri, remoteAddress(request), detailJson);
        } catch (Exception auditException) {
            log.error("Audit record failed, resourceType={}, action={}, result={}, traceId={}",
                    action.resourceType(), action.action(), result, TraceIdContext.getOrCreate(), auditException);
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest() : null;
    }

    private String resourceId(String uri) {
        if (uri == null) return null;
        Matcher matcher = NUMERIC_PATH_SEGMENT.matcher(uri);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String remoteAddress(HttpServletRequest request) {
        // The application does not have a trusted-proxy allowlist, so forwarded headers
        // can be supplied by the client and must not be treated as authoritative audit data.
        return request == null ? null : request.getRemoteAddr();
    }
}
