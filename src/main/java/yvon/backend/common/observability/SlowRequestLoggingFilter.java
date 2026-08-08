package yvon.backend.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import yvon.backend.common.trace.TraceIdContext;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SlowRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SlowRequestLoggingFilter.class);
    private final long thresholdMs;

    public SlowRequestLoggingFilter(@Value("${taskflow.observability.slow-request.threshold-ms:1000}") long thresholdMs) {
        this.thresholdMs = Math.max(1, thresholdMs);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            if (elapsedMs >= thresholdMs) {
                log.warn("Slow request, method={}, uri={}, status={}, elapsedMs={}, traceId={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs,
                        TraceIdContext.getOrCreate());
            }
        }
    }
}
