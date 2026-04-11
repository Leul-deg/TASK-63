package com.reslife.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.reslife.api")
@EnableScheduling
public class JpaConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
