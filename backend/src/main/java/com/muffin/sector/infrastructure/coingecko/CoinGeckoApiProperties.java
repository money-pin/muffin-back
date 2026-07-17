package com.muffin.sector.infrastructure.coingecko;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "coingecko.api")
public record CoinGeckoApiProperties(
        String baseUrl,
        String apiKey,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {}
