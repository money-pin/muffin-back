package com.muffin.sector.infrastructure.toss;

import com.muffin.sector.infrastructure.toss.dto.TossTokenResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OAuth2 Client Credentials Grant로 토스증권 액세스 토큰을 발급받고 만료 전까지 캐싱한다.
 *
 * <p>클라이언트 인증 방식(Basic 헤더 vs 폼 바디)은 표준 OAuth2 관행(RFC 6749)을 따라 Basic 헤더로 구현했으며, 실제 토스증권
 * 문서로 재확인이 필요하다(§5.1).
 */
@Component
public class TossTokenProvider {

    private static final String TOKEN_PATH = "/oauth2/token";
    private static final Duration EXPIRY_BUFFER = Duration.ofSeconds(30);

    private final RestClient tossRestClient;
    private final TossApiClient tossApiClient;
    private final TossApiProperties properties;
    private final Clock clock;

    private CachedToken cachedToken;

    public TossTokenProvider(
            RestClient tossRestClient, TossApiClient tossApiClient, TossApiProperties properties, Clock clock) {
        this.tossRestClient = tossRestClient;
        this.tossApiClient = tossApiClient;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized String getAccessToken() {
        Instant now = clock.instant();
        if (cachedToken == null || !cachedToken.isValidAt(now)) {
            cachedToken = fetchToken(now);
        }
        return cachedToken.accessToken();
    }

    private CachedToken fetchToken(Instant now) {
        TossTokenResponse response = tossApiClient.execute(() -> tossRestClient
                .post()
                .uri(TOKEN_PATH)
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TossTokenResponse.class));

        Instant expiresAt = now.plusSeconds(response.expiresIn()).minus(EXPIRY_BUFFER);
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValidAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
