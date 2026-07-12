package com.muffin.sector.infrastructure.toss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import com.muffin.sector.infrastructure.toss.exception.TossRateLimitExceededException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossApiClientTest {

    private static final String URI = "http://toss.test/api/v1/candles";

    private RestClient restClient;
    private MockRestServiceServer server;
    private TossApiClient client;

    private void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://toss.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new TossApiClient(new TossRateLimiter(Clock.systemUTC(), Duration.ZERO));
    }

    @Test
    @DisplayName("성공 응답이면 바디를 그대로 반환한다")
    void execute_returnsBodyOnSuccess() {
        setUp();
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"price\":10000}", MediaType.APPLICATION_JSON));

        PriceDto result = client.execute(
                () -> restClient.get().uri("/api/v1/candles").retrieve().body(PriceDto.class));

        assertEquals(10000, result.price());
        server.verify();
    }

    @Test
    @DisplayName("429이면 Retry-After만큼 대기 후 재시도해서 성공한다")
    void execute_retriesOnTooManyRequests_thenSucceeds() {
        setUp();
        server.expect(requestTo(URI)).andRespond(withTooManyRequests().header(HttpHeaders.RETRY_AFTER, "0"));
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"price\":10000}", MediaType.APPLICATION_JSON));

        PriceDto result = client.execute(
                () -> restClient.get().uri("/api/v1/candles").retrieve().body(PriceDto.class));

        assertEquals(10000, result.price());
        server.verify();
    }

    @Test
    @DisplayName("429가 최대 재시도 횟수만큼 반복되면 레이트리밋 예외를 던진다")
    void execute_throwsRateLimitExceededException_afterMaxAttempts() {
        setUp();
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(URI))
                    .andRespond(withTooManyRequests()
                            .header(HttpHeaders.RETRY_AFTER, "0")
                            .body("{\"error\":{\"requestId\":\"r1\",\"code\":\"RATE_LIMIT\",\"message\":\"too many\"}}")
                            .contentType(MediaType.APPLICATION_JSON));
        }

        TossRateLimitExceededException exception = assertThrows(
                TossRateLimitExceededException.class,
                () -> client.execute(
                        () -> restClient.get().uri("/api/v1/candles").retrieve().body(PriceDto.class)));

        assertEquals("r1", exception.getRequestId());
        assertEquals("RATE_LIMIT", exception.getTossCode());
        server.verify();
    }

    @Test
    @DisplayName("4xx 에러 응답은 재시도 없이 바로 TossApiException으로 변환한다")
    void execute_throwsTossApiException_on4xxError() {
        setUp();
        server.expect(requestTo(URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body(
                                "{\"error\":{\"requestId\":\"r2\",\"code\":\"INVALID_SYMBOL\",\"message\":\"no such symbol\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        TossApiException exception = assertThrows(
                TossApiException.class,
                () -> client.execute(
                        () -> restClient.get().uri("/api/v1/candles").retrieve().body(PriceDto.class)));

        assertEquals("r2", exception.getRequestId());
        assertEquals("INVALID_SYMBOL", exception.getTossCode());
        server.verify();
    }

    @Test
    @DisplayName("일시적 네트워크 오류는 재시도 후 성공하면 정상 반환한다")
    void execute_retriesOnTransientNetworkError_thenSucceeds() {
        setUp();
        server.expect(requestTo(URI)).andRespond(request -> {
            throw new IOException("connection reset");
        });
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"price\":10000}", MediaType.APPLICATION_JSON));

        PriceDto result = client.execute(
                () -> restClient.get().uri("/api/v1/candles").retrieve().body(PriceDto.class));

        assertEquals(10000, result.price());
        server.verify();
    }

    private record PriceDto(int price) {}
}
