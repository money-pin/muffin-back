package com.muffin.sector.infrastructure.toss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossTokenProviderTest {

    private static final String TOSS_BASE_URL = "http://toss.test";
    private static final String TOKEN_URI = TOSS_BASE_URL + "/oauth2/token";
    private static final TossApiProperties PROPERTIES = new TossApiProperties(
            TOSS_BASE_URL, "client-id", "client-secret", Duration.ofSeconds(3), Duration.ofSeconds(5));

    @Test
    @DisplayName("만료 전까지는 캐싱된 토큰을 그대로 반환한다")
    void getAccessToken_cachesTokenUntilExpiry() {
        RestClient.Builder builder = RestClient.builder().baseUrl(TOSS_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        TossApiClient tossApiClient = new TossApiClient(new TossRateLimiter(Clock.systemUTC(), Duration.ZERO));
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        TossTokenProvider tokenProvider = new TossTokenProvider(restClient, tossApiClient, PROPERTIES, clock);

        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content()
                        .formDataContains(Map.of(
                                "grant_type", "client_credentials",
                                "client_id", "client-id",
                                "client_secret", "client-secret")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        String first = tokenProvider.getAccessToken();
        clock.advance(Duration.ofMinutes(10));
        String second = tokenProvider.getAccessToken();

        assertEquals("token-1", first);
        assertEquals("token-1", second);
        server.verify();
    }

    @Test
    @DisplayName("토큰이 만료되면 새로 발급받는다")
    void getAccessToken_refetches_afterExpiry() {
        RestClient.Builder builder = RestClient.builder().baseUrl(TOSS_BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        TossApiClient tossApiClient = new TossApiClient(new TossRateLimiter(Clock.systemUTC(), Duration.ZERO));
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        TossTokenProvider tokenProvider = new TossTokenProvider(restClient, tossApiClient, PROPERTIES, clock);

        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-1\",\"token_type\":\"Bearer\",\"expires_in\":60}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-2\",\"token_type\":\"Bearer\",\"expires_in\":60}",
                        MediaType.APPLICATION_JSON));

        String first = tokenProvider.getAccessToken();
        clock.advance(Duration.ofMinutes(5));
        String second = tokenProvider.getAccessToken();

        assertEquals("token-1", first);
        assertEquals("token-2", second);
        server.verify();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
