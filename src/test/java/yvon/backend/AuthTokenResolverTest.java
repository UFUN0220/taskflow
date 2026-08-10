package yvon.backend;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import yvon.backend.auth.AuthProperties;
import yvon.backend.auth.AuthTokenResolver;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenResolverTest {

    private final AuthProperties properties = properties();
    private final AuthTokenResolver resolver = new AuthTokenResolver(properties);

    @Test
    void resolvesConfiguredBrowserCookie() {
        assertThat(resolver.resolveCookies(new Cookie[]{new Cookie("TASKFLOW_ACCESS", "cookie-token")}))
                .isEqualTo("cookie-token");
        assertThat(resolver.resolveCookieHeader("other=value; TASKFLOW_ACCESS=cookie-token"))
                .isEqualTo("cookie-token");
    }

    @Test
    void bearerHeaderTakesPrecedenceForCompatibilityClients() {
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bearer-token");
        request.setCookies(new Cookie("TASKFLOW_ACCESS", "cookie-token"));

        assertThat(resolver.resolve(request)).isEqualTo("bearer-token");
    }

    private AuthProperties properties() {
        AuthProperties result = new AuthProperties();
        result.setJwtSecret("01234567890123456789012345678901");
        return result;
    }
}
