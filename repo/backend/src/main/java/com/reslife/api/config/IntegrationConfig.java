package com.reslife.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.domain.integration.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Infrastructure beans for the local integration layer:
 * <ul>
 *   <li>{@link IntegrationAuthFilter} — registered in the Spring Security filter chain
 *       (not as a standalone servlet filter, to prevent double execution)</li>
 *   <li>{@code webhookRestTemplate} — {@link RestTemplate} with 5 s connect / 10 s read
 *       timeouts and a no-op error handler so non-2xx responses are returned normally</li>
 * </ul>
 */
@Configuration
public class IntegrationConfig {

    /**
     * The auth filter as a Spring bean so it can be injected into {@link SecurityConfig}.
     * The matching {@link FilterRegistrationBean} disables auto-registration as a
     * plain servlet filter, ensuring the filter runs only once (inside the security chain).
     */
    @Bean
    public IntegrationAuthFilter integrationAuthFilter(
            IntegrationKeyRepository keyRepo,
            HmacService hmacService,
            IntegrationRateLimiter rateLimiter,
            IntegrationAuditLogRepository auditRepo,
            ObjectMapper objectMapper) {
        return new IntegrationAuthFilter(keyRepo, hmacService, rateLimiter, auditRepo, objectMapper);
    }

    /** Prevents Spring Boot from auto-registering the filter as a plain servlet filter. */
    @Bean
    public FilterRegistrationBean<IntegrationAuthFilter> integrationAuthFilterRegistration(
            IntegrationAuthFilter filter) {
        FilterRegistrationBean<IntegrationAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** RestTemplate used exclusively by {@link WebhookService} for outgoing webhook calls. */
    @Bean("webhookRestTemplate")
    public RestTemplate webhookRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);

        RestTemplate template = new RestTemplate(factory);
        // Never throw on non-2xx — let WebhookService inspect the status itself
        template.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse r) { return false; }
            @Override public void    handleError(org.springframework.http.client.ClientHttpResponse r) {}
        });
        return template;
    }
}
