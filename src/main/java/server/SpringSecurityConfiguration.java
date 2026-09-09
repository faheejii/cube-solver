package server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Stateless Spring Security wiring for the legacy cookie/session and route authorization policy. */
@Configuration
@EnableWebSecurity
class SpringSecurityConfiguration {
    @Bean
    SpringSessionAuthenticationProvider sessionAuthenticationProvider(SpringAuthService authService) {
        return new SpringSessionAuthenticationProvider(authService);
    }

    @Bean
    AuthenticationManager authenticationManager(SpringSessionAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    SpringSessionAuthenticationFilter sessionAuthenticationFilter(AuthenticationManager manager) {
        return new SpringSessionAuthenticationFilter(manager);
    }

    @Bean
    FilterRegistrationBean<SpringRequestPolicyFilter> requestPolicyFilterRegistration(
            OperationalMetrics metrics
    ) {
        var registration = new FilterRegistrationBean<>(new SpringRequestPolicyFilter(metrics));
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SpringSessionAuthenticationFilter sessionFilter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health/**",
                                "/api/metrics",
                                "/api/auth/**",
                                "/api/solve",
                                "/api/solve-jobs/**",
                                "/assets/**",
                                "/favicon.ico",
                                "/"
                        ).permitAll()
                        .requestMatchers("/api/solves/**", "/api/stats", "/api/algorithms").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> write(SpringRequestSupport.error(401, "Authentication required"), response);
    }

    private static AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> write(SpringRequestSupport.error(403, "Forbidden"), response);
    }

    private static void write(org.springframework.http.ResponseEntity<String> entity,
                              jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setStatus(entity.getStatusCode().value());
        entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.setHeader(name, value)));
        if (entity.getBody() != null) {
            response.setContentType("application/json");
            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
            response.getWriter().write(entity.getBody());
        }
    }
}
