package yvon.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import yvon.backend.audit.AuditAction;
import yvon.backend.audit.AuditAspect;
import yvon.backend.audit.AuditLogService;
import yvon.backend.common.trace.TraceIdContext;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AuditAspect aspect = new AuditAspect(auditLogService, new ObjectMapper());

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        TraceIdContext.clear();
    }

    @Test
    void recordsSuccessWithTraceAndSafeMetadataOnly() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tasks/42/submit");
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "192.0.2.10, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TraceIdContext.set("trace-stage12");

        ProceedingJoinPoint joinPoint = joinPointReturning("password=never-recorded");
        aspect.audit(joinPoint, annotation("TASK", "SUBMIT"));

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("trace-stage12"), any(), eq("TASK"), eq("42"), eq("SUBMIT"),
                eq("SUCCESS"), eq("POST"), eq("/api/tasks/42/submit"), eq("198.51.100.7"), detail.capture());
        assertFalse(detail.getValue().contains("password=never-recorded"));
        assertFalse(detail.getValue().contains("token"));
    }

    @Test
    void recordsFailureAndRethrowsOriginalException() throws Throwable {
        RuntimeException failure = new IllegalStateException("business failure");
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenThrow(failure);

        assertThrows(IllegalStateException.class, () -> aspect.audit(joinPoint, annotation("ROLE", "UPDATE")));

        verify(auditLogService).record(any(), any(), eq("ROLE"), any(), eq("UPDATE"), eq("FAILURE"),
                any(), any(), any(), any());
    }

    private ProceedingJoinPoint joinPointReturning(Object value) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(value);
        return joinPoint;
    }

    private AuditAction annotation(String resourceType, String action) throws NoSuchMethodException {
        Method method = Fixture.class.getDeclaredMethod("operation");
        return new AuditAction() {
            @Override public String resourceType() { return resourceType; }
            @Override public String action() { return action; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return AuditAction.class; }
        };
    }

    private static final class Fixture {
        @AuditAction(resourceType = "FIXTURE", action = "OPERATION")
        void operation() { }
    }
}
