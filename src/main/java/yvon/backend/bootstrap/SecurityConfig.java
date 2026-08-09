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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import yvon.backend.auth.ApiSecurityResponseWriter;
import yvon.backend.auth.JwtAuthenticationFilter;

/**
 * API security boundary. Stage 3 uses stateless JWT authentication; database-free tests can
 * explicitly disable it with taskflow.auth.enabled=false.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Value("${taskflow.auth.enabled:true}") boolean authEnabled,
            @Value("${taskflow.security.headers.csp:default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; connect-src 'self' ws: wss:; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; font-src 'self' data:}") String contentSecurityPolicy,
            ObjectProvider<JwtAuthenticationFilter> jwtFilter,
            ObjectProvider<ApiSecurityResponseWriter> responseWriterProvider) throws Exception {
        // REST and STOMP use an explicit Bearer token instead of a browser cookie.
        // If authentication moves to cookies, CSRF must be enabled and tested here.
        http.csrf(AbstractHttpConfigurer::disable)
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
