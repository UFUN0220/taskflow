package yvon.backend.bootstrap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import yvon.backend.auth.ApiSecurityResponseWriter;
import yvon.backend.auth.AuthProperties;
import yvon.backend.auth.JwtAuthenticationFilter;

/**
 * API security boundary. JWT remains stateless at the protocol level while Redis
 * supplies active-session checks; browser clients use an HttpOnly cookie and
 * Bearer clients remain supported for scripts and non-browser integrations.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Value("${taskflow.auth.enabled:true}") boolean authEnabled,
            @Value("${taskflow.security.headers.csp:default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; connect-src 'self' ws: wss:; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; font-src 'self' data:}") String contentSecurityPolicy,
            ObjectProvider<AuthProperties> authPropertiesProvider,
            ObjectProvider<JwtAuthenticationFilter> jwtFilter,
            ObjectProvider<ApiSecurityResponseWriter> responseWriterProvider) throws Exception {
        AuthProperties authProperties = authPropertiesProvider.getIfAvailable(AuthProperties::new);
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        csrfRepository.setCookieCustomizer(builder -> builder
                .path(authProperties.getBrowserCookie().getPath())
                .secure(authProperties.getBrowserCookie().isSecure())
                .sameSite(authProperties.getBrowserCookie().getSameSite())
                .httpOnly(false));

        http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(new NegatedRequestMatcher(new CookieCsrfRequestMatcher(
                                authProperties.getBrowserCookie().getName()))))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)));

        if (authEnabled) {
            ApiSecurityResponseWriter responseWriter = responseWriterProvider.getIfAvailable();
            if (responseWriter == null) {
                throw new IllegalStateException("ApiSecurityResponseWriter is required when authentication is enabled");
            }
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/health", "/actuator/health", "/api/auth/login",
                                    "/api/auth/csrf",
                                    "/ws/notifications", "/ws/notifications/**",
                                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint((request, response, authException) ->
                                    responseWriter.write(response, yvon.backend.common.error.BusinessErrorCode.UNAUTHORIZED))
                            .accessDeniedHandler((request, response, accessDeniedException) ->
                                    responseWriter.write(response, yvon.backend.common.error.BusinessErrorCode.FORBIDDEN)));
            JwtAuthenticationFilter filter = jwtFilter.getIfAvailable();
            if (filter != null) {
                http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
            }
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        }
        return http.build();
    }
}
