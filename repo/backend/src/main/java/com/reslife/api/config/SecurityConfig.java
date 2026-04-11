package com.reslife.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.security.AccountStatusFilter;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // enables @PreAuthorize on admin controllers
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final AccountStatusFilter accountStatusFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          AccountStatusFilter accountStatusFilter,
                          ObjectMapper objectMapper) {
        this.userDetailsService = userDetailsService;
        this.accountStatusFilter = accountStatusFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           IntegrationAuthFilter integrationAuthFilter) throws Exception {
        http
            // CSRF: cookie-based token repository so the React frontend can read the
            // XSRF-TOKEN cookie and send it back as X-XSRF-TOKEN on mutating requests.
            // CsrfTokenRequestAttributeHandler (not XorCsrf…) keeps the token un-masked
            // for simple JSON clients.
            // Integration endpoints use HMAC auth and are called by on-prem devices,
            // not browsers — CSRF does not apply.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/auth/login", "/api/integrations/**")
            )
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(5)
            )
            // IntegrationAuthFilter runs before the session-based auth filter so it
            // can short-circuit invalid HMAC requests before any session is consulted.
            .addFilterBefore(integrationAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // Per-request account-status check for session-authenticated users
            .addFilterAfter(accountStatusFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/login", "/api/auth/csrf").permitAll()
                .requestMatchers("/api/health").permitAll()
                // Integration endpoints: Spring Security permits all; HMAC auth is
                // enforced by IntegrationAuthFilter before the request reaches the controller.
                .requestMatchers("/api/integrations/**").permitAll()
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "HOUSING_ADMINISTRATOR")
                .anyRequest().authenticated()
            )
            // Return JSON 401 — never redirect to a login page
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                            Map.of("error", "Authentication required"));
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(res.getWriter(),
                            Map.of("error", "Access denied"));
                })
            )
            // Disable built-in form login and HTTP Basic — auth is via custom /api/auth/login
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable()); // handled by /api/auth/logout endpoint

        return http.build();
    }

    // -----------------------------------------------------------------------
    // Authentication infrastructure
    // -----------------------------------------------------------------------

    /** BCrypt with work factor 12 as required. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * DaoAuthenticationProvider wired to our UserDetailsService and password encoder.
     * hideUserNotFoundExceptions=true (the default) converts UsernameNotFoundException
     * to BadCredentialsException so callers cannot distinguish "no such user" from
     * "wrong password".
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // hideUserNotFoundExceptions defaults to true — keep it that way
        return new ProviderManager(provider);
    }

    /**
     * Explicit SecurityContextRepository bean so AuthController can save
     * the context to the session without relying on a thread-local side effect.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
