package com.muffin.sector.infrastructure.toss;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "toss.api")
public record TossApiProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {}
