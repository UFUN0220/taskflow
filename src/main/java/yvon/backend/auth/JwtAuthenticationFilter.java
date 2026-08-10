package yvon.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtTokenService tokenService;
    private final AuthSessionService sessionService;
    private final UserDetailsService userDetailsService;
    private final AuthTokenResolver tokenResolver;

    public JwtAuthenticationFilter(JwtTokenService tokenService, AuthSessionService sessionService,
                                   UserDetailsService userDetailsService, AuthTokenResolver tokenResolver) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.userDetailsService = userDetailsService;
        this.tokenResolver = tokenResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = tokenResolver.resolve(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        if (token.isBlank()) {
            return;
        }
        try {
            Claims claims = tokenService.parse(token);
            if (!sessionService.isActive(claims)) {
                return;
            }
            UserDetails details = userDetailsService.loadUserByUsername(claims.getSubject());
            Number tokenUserId = claims.get("uid", Number.class);
            if (details instanceof UserPrincipal principal
                    && tokenUserId != null && tokenUserId.longValue() == principal.userId()) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Ignoring invalid access token, traceId={}",
                    yvon.backend.common.trace.TraceIdContext.getOrCreate());
        }
    }
}
