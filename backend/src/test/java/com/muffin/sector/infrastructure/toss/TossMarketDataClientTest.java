package com.muffin.sector.infrastructure.toss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossMarketDataClientTest {

    private static final String TOSS_BASE_URL = "http://toss.test";

    private RestClient restClient;
    private MockRestServiceServer server;
    private TossMarketDataClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(TOSS_BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        TossApiClient tossApiClient = new TossApiClient(new TossRateLimiter(Clock.systemUTC(), Duration.ZERO));
        TossApiProperties properties = new TossApiProperties(
                TOSS_BASE_URL, "client-id", "client-secret", Duration.ofSeconds(3), Duration.ofSeconds(5));
        TossTokenProvider tokenProvider =
                new TossTokenProvider(restClient, tossApiClient, properties, Clock.systemUTC());
        client = new TossMarketDataClient(restClient, tossApiClient, tokenProvider);

        server.expect(requestTo(TOSS_BASE_URL + "/oauth2/token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("일봉 조회 시 Bearer 토큰을 담아 요청하고 응답을 그대로 반환한다")
    void getDailyCandle_returnsParsedResponse() {
        setUp();
        LocalDate date = LocalDate.of(2026, 7, 10);
        server.expect(requestToUriTemplate(
                        TOSS_BASE_URL + "/api/v1/candles?symbol={symbol}&date={date}", "459580", date))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(
                        "{\"base_date\":\"2026-07-10\",\"open_price\":10000,\"close_price\":10500,"
                                + "\"high_price\":10600,\"low_price\":9900}",
                        MediaType.APPLICATION_JSON));

        TossCandleResponse response = client.getDailyCandle("459580", date);

        assertEquals(date, response.baseDate());
        assertEquals(10000L, response.openPrice());
        assertEquals(10500L, response.closePrice());
        server.verify();
    }

    @Test
    @DisplayName("거래일 조회 시 Bearer 토큰을 담아 요청하고 응답을 그대로 반환한다")
    void getMarketCalendar_returnsParsedResponse() {
        setUp();
        LocalDate date = LocalDate.of(2026, 7, 11);
        server.expect(requestToUriTemplate(TOSS_BASE_URL + "/api/v1/market-calendar/KR?date={date}", date))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(
                        withSuccess("{\"date\":\"2026-07-11\",\"is_trading_day\":true}", MediaType.APPLICATION_JSON));

        TossMarketCalendarResponse response = client.getMarketCalendar(date);

        assertEquals(date, response.date());
        assertTrue(response.tradingDay());
        server.verify();
    }
}
