package yvon.backend.bootstrap;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Requires CSRF for browser-style writes while preserving explicit Bearer API compatibility. */
public final class CookieCsrfRequestMatcher implements RequestMatcher {

    public CookieCsrfRequestMatcher(String cookieName) {
        // Keep the constructor stable for profile wiring; the matcher is browser-vs-Bearer based.
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        if (HttpMethod.GET.matches(request.getMethod())
                || HttpMethod.HEAD.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || HttpMethod.TRACE.matches(request.getMethod())) {
            return false;
        }
        if ("/api/auth/login".equals(request.getRequestURI())) return false;
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && !authorization.substring("Bearer ".length()).isBlank()) {
            return false;
        }
        return true;
    }
}
