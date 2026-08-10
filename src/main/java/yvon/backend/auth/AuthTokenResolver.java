package yvon.backend.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Resolves Bearer credentials first, then the profile-controlled browser cookie. */
@Component
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthTokenResolver {

    public static final String WEBSOCKET_COOKIE_TOKEN_ATTRIBUTE =
            AuthTokenResolver.class.getName() + ".cookieToken";

    private final AuthProperties properties;

    public AuthTokenResolver(AuthProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            if (!token.isBlank()) {
                return token;
            }
        }
        return resolveCookies(request.getCookies());
    }

    public String resolveCookies(Cookie[] cookies) {
        if (!properties.getBrowserCookie().isEnabled() || cookies == null) {
            return null;
        }
        String cookieName = properties.getBrowserCookie().getName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public String resolveCookieHeader(String cookieHeader) {
        if (!properties.getBrowserCookie().isEnabled() || cookieHeader == null) {
            return null;
        }
        String cookieName = properties.getBrowserCookie().getName();
        for (String part : cookieHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && cookieName.equals(pair[0]) && !pair[1].isBlank()) {
                return pair[1].trim();
            }
        }
        return null;
    }

    public String cookieName() {
        return properties.getBrowserCookie().getName();
    }

    public AuthProperties.BrowserCookie browserCookie() {
        return properties.getBrowserCookie();
    }
}
