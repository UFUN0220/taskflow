package yvon.backend.bootstrap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
            ObjectProvider<JwtAuthenticationFilter> jwtFilter,
            ObjectProvider<ApiSecurityResponseWriter> responseWriterProvider) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (authEnabled) {
            ApiSecurityResponseWriter responseWriter = responseWriterProvider.getIfAvailable();
            if (responseWriter == null) {
                throw new IllegalStateException("ApiSecurityResponseWriter is required when authentication is enabled");
            }
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/health", "/actuator/health", "/api/auth/login",
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
