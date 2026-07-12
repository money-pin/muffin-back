package com.muffin.sector.infrastructure.toss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossMarketDataClientTest {

    private static final String TOSS_BASE_URL = "http://toss.test";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
    @DisplayName("일봉 조회 시 Bearer 토큰을 담아 before 커서로 1건을 요청하고, 요청한 날짜의 캔들을 반환한다")
    void getDailyCandle_returnsCandleForRequestedDate() {
        setUp();
        LocalDate date = LocalDate.of(2026, 7, 10);
        String before =
                date.plusDays(1).atStartOfDay(KST).minusSeconds(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        server.expect(requestToUriTemplate(
                        TOSS_BASE_URL
                                + "/api/v1/candles?symbol={symbol}&interval={interval}&count={count}&before={before}",
                        "459580",
                        "1d",
                        1,
                        before))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(
                        "{\"result\":{\"candles\":[{\"timestamp\":\"2026-07-10T15:30:00+09:00\","
                                + "\"openPrice\":\"10000\",\"closePrice\":\"10500\",\"highPrice\":\"10600\","
                                + "\"lowPrice\":\"9900\",\"volume\":\"12345\",\"currency\":\"KRW\"}],"
                                + "\"nextBefore\":\"2026-07-09T15:30:00+09:00\"}}",
                        MediaType.APPLICATION_JSON));

        Optional<Candle> result = client.getDailyCandle("459580", date);

        assertTrue(result.isPresent());
        assertEquals("10000", result.get().openPrice());
        assertEquals("10500", result.get().closePrice());
        server.verify();
    }

    @Test
    @DisplayName("요청한 날짜에 해당하는 캔들이 없으면(휴장일 등) 비어있는 결과를 반환한다")
    void getDailyCandle_returnsEmpty_whenNoCandleMatchesDate() {
        setUp();
        LocalDate date = LocalDate.of(2026, 7, 11);
        String before =
                date.plusDays(1).atStartOfDay(KST).minusSeconds(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        server.expect(requestToUriTemplate(
                        TOSS_BASE_URL
                                + "/api/v1/candles?symbol={symbol}&interval={interval}&count={count}&before={before}",
                        "459580",
                        "1d",
                        1,
                        before))
                .andRespond(withSuccess(
                        "{\"result\":{\"candles\":[{\"timestamp\":\"2026-07-10T15:30:00+09:00\","
                                + "\"openPrice\":\"10000\",\"closePrice\":\"10500\",\"highPrice\":\"10600\","
                                + "\"lowPrice\":\"9900\",\"volume\":\"12345\",\"currency\":\"KRW\"}],"
                                + "\"nextBefore\":null}}",
                        MediaType.APPLICATION_JSON));

        Optional<Candle> result = client.getDailyCandle("459580", date);

        assertTrue(result.isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("일봉 응답 본문이 없으면 명시적인 API 예외를 던진다")
    void getDailyCandle_throwsApiException_whenResponseBodyMissing() {
        setUp();
        LocalDate date = LocalDate.of(2026, 7, 10);
        String before =
                date.plusDays(1).atStartOfDay(KST).minusSeconds(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        server.expect(requestToUriTemplate(
                        TOSS_BASE_URL
                                + "/api/v1/candles?symbol={symbol}&interval={interval}&count={count}&before={before}",
                        "459580",
                        "1d",
                        1,
                        before))
                .andRespond(withSuccess());

        TossApiException exception = assertThrows(TossApiException.class, () -> client.getDailyCandle("459580", date));

        assertEquals("INVALID_RESPONSE", exception.getTossCode());
        server.verify();
    }

    @Test
    @DisplayName("거래일 조회 시 Bearer 토큰을 담아 요청하고 result를 그대로 반환한다")
    void getMarketCalendar_returnsUnwrappedResult() {
        setUp();
        LocalDate date = LocalDate.of(2026, 7, 11);
        server.expect(requestToUriTemplate(TOSS_BASE_URL + "/api/v1/market-calendar/KR?date={date}", date))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(
                        "{\"result\":{\"today\":{\"date\":\"2026-07-11\",\"integrated\":{"
                                + "\"regularMarket\":{\"startTime\":\"2026-07-11T09:00:00+09:00\","
                                + "\"endTime\":\"2026-07-11T15:30:00+09:00\"}}}}}",
                        MediaType.APPLICATION_JSON));

        TossMarketCalendarResponse.Result result = client.getMarketCalendar(date);

        assertEquals(date, result.today().date());
        assertEquals(
                "2026-07-11T09:00:00+09:00",
                result.today().integrated().regularMarket().startTime());
        server.verify();
    }

    @Test
    @DisplayName("거래일 응답 result가 없으면 명시적인 API 예외를 던진다")
    void getMarketCalendar_throwsApiException_whenResultMissing() {
        setUp();
        LocalDate date = LocalDate.of(2026, 7, 11);
        server.expect(requestToUriTemplate(TOSS_BASE_URL + "/api/v1/market-calendar/KR?date={date}", date))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        TossApiException exception = assertThrows(TossApiException.class, () -> client.getMarketCalendar(date));

        assertEquals("INVALID_RESPONSE", exception.getTossCode());
        server.verify();
    }
}
