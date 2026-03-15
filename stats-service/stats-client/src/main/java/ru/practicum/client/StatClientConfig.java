package ru.practicum.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatClientConfig {

    @Value("${stats.base-url:http://localhost:9090}")
    private String baseUrl;

    @Value("${spring.application.name}")
    private String serviceName;

    @Bean
    public StatClient statClient() {
        return new StatClient(baseUrl, serviceName);
    }
}