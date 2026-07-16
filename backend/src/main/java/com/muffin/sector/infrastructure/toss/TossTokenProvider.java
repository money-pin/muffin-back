package com.muffin.sector.infrastructure.toss;

import com.muffin.sector.infrastructure.toss.dto.TossTokenResponse;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth2 Client Credentials Grant로 토스증권 액세스 토큰을 발급받고 만료 전까지 캐싱한다.
 *
 * <p>{@code client_id}/{@code client_secret}은 Basic 인증 헤더가 아니라 {@code grant_type}과 함께
 * {@code application/x-www-form-urlencoded} 요청 본문에 담아 전송한다(공식 문서 AuthApi 기준 확인 완료).
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
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        TossTokenResponse response = tossApiClient.execute(() -> tossRestClient
                .post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TossTokenResponse.class));

        if (response == null || response.accessToken() == null) {
            throw new TossApiException(null, "INVALID_RESPONSE", null, "토스증권 API의 토큰 응답 형식이 올바르지 않습니다.");
        }

        Instant expiresAt = now.plusSeconds(response.expiresIn()).minus(EXPIRY_BUFFER);
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValidAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
