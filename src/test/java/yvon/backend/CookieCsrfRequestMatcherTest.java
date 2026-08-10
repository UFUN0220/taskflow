package yvon.backend;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import yvon.backend.bootstrap.CookieCsrfRequestMatcher;

import static org.assertj.core.api.Assertions.assertThat;

class CookieCsrfRequestMatcherTest {

    private final CookieCsrfRequestMatcher matcher = new CookieCsrfRequestMatcher("TASKFLOW_ACCESS");

    @Test
    void protectsUnsafeCookieAuthenticatedRequestsOnly() {
        MockHttpServletRequest post = request("POST");
        post.setCookies(new Cookie("TASKFLOW_ACCESS", "cookie-token"));
        assertThat(matcher.matches(post)).isTrue();

        MockHttpServletRequest get = request("GET");
        get.setCookies(new Cookie("TASKFLOW_ACCESS", "cookie-token"));
        assertThat(matcher.matches(get)).isFalse();

        MockHttpServletRequest bearerOnly = request("POST");
        bearerOnly.addHeader("Authorization", "Bearer api-token");
        assertThat(matcher.matches(bearerOnly)).isFalse();

        MockHttpServletRequest browserWrite = request("POST");
        assertThat(matcher.matches(browserWrite)).isTrue();
    }

    @Test
    void csrfFilterRejectsMissingTokenAndAcceptsMatchingHeader() throws Exception {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfFilter filter = new CsrfFilter(repository);
        filter.setRequireCsrfProtectionMatcher(matcher);
        filter.setRequestHandler(new CsrfTokenRequestAttributeHandler());

        MockHttpServletRequest missing = request("POST");
        missing.setCookies(new Cookie("TASKFLOW_ACCESS", "cookie-token"));
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        java.util.concurrent.atomic.AtomicBoolean missingChainCalled = new java.util.concurrent.atomic.AtomicBoolean();
        filter.doFilter(missing, missingResponse, (request, response) -> missingChainCalled.set(true));
        assertThat(missingResponse.getStatus()).isEqualTo(403);
        assertThat(missingChainCalled).isFalse();

        MockHttpServletRequest valid = request("POST");
        valid.setCookies(new Cookie("TASKFLOW_ACCESS", "cookie-token"),
                new Cookie("XSRF-TOKEN", "csrf-token"));
        valid.addHeader("X-XSRF-TOKEN", "csrf-token");
        MockHttpServletResponse validResponse = new MockHttpServletResponse();
        java.util.concurrent.atomic.AtomicBoolean validChainCalled = new java.util.concurrent.atomic.AtomicBoolean();
        filter.doFilter(valid, validResponse, (request, response) -> validChainCalled.set(true));
        assertThat(validResponse.getStatus()).isEqualTo(200);
        assertThat(validChainCalled).isTrue();
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        return request;
    }
}
