package com.muffin.sector.infrastructure.coingecko;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import com.muffin.sector.infrastructure.coingecko.exception.CoinGeckoApiException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoinGeckoApiClientTest {

    private static final String URI = "http://coingecko.test/api/v3/simple/price";

    private RestClient restClient;
    private MockRestServiceServer server;
    private CoinGeckoApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://coingecko.test");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        client = new CoinGeckoApiClient();
    }

    @Test
    @DisplayName("성공 응답이면 바디를 그대로 반환한다")
    void execute_returnsBodyOnSuccess() {
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"price\":10000}", MediaType.APPLICATION_JSON));

        PriceDto result = client.execute(
                () -> restClient.get().uri("/api/v3/simple/price").retrieve().body(PriceDto.class));

        assertEquals(10000, result.price());
        server.verify();
    }

    @Test
    @DisplayName("429이면 Retry-After만큼 대기 후 재시도해서 성공한다")
    void execute_retriesOnTooManyRequests_thenSucceeds() {
        server.expect(requestTo(URI)).andRespond(withTooManyRequests().header(HttpHeaders.RETRY_AFTER, "0"));
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"price\":10000}", MediaType.APPLICATION_JSON));

        PriceDto result = client.execute(
                () -> restClient.get().uri("/api/v3/simple/price").retrieve().body(PriceDto.class));

        assertEquals(10000, result.price());
        server.verify();
    }

    @Test
    @DisplayName("429가 최대 재시도 횟수만큼 반복되면 API 예외를 던진다")
    void execute_throwsApiException_afterMaxAttemptsOnTooManyRequests() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(URI)).andRespond(withTooManyRequests().header(HttpHeaders.RETRY_AFTER, "0"));
        }

        CoinGeckoApiException exception = assertThrows(
                CoinGeckoApiException.class,
                () -> client.execute(() ->
                        restClient.get().uri("/api/v3/simple/price").retrieve().body(PriceDto.class)));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getHttpStatus());
        server.verify();
    }

    @Test
    @DisplayName("4xx 에러 응답은 재시도 없이 바로 CoinGeckoApiException으로 변환한다")
    void execute_throwsApiException_on4xxError() {
        server.expect(requestTo(URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        CoinGeckoApiException exception = assertThrows(
                CoinGeckoApiException.class,
                () -> client.execute(() ->
                        restClient.get().uri("/api/v3/simple/price").retrieve().body(PriceDto.class)));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getHttpStatus());
        server.verify();
    }

    @Test
    @DisplayName("5xx 서버 오류는 재시도한 뒤 성공 응답을 반환한다")
    void execute_retriesOnServerError_thenSucceeds() {
        server.expect(requestTo(URI)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"price\":10000}", MediaType.APPLICATION_JSON));

        PriceDto result = client.execute(
                () -> restClient.get().uri("/api/v3/simple/price").retrieve().body(PriceDto.class));

        assertEquals(10000, result.price());
        server.verify();
    }

    @Test
    @DisplayName("5xx 서버 오류가 최대 시도 횟수만큼 반복되면 API 예외를 던진다")
    void execute_throwsApiException_afterServerErrorMaxAttempts() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(URI)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        }

        CoinGeckoApiException exception = assertThrows(
                CoinGeckoApiException.class,
                () -> client.execute(() ->
                        restClient.get().uri("/api/v3/simple/price").retrieve().body(PriceDto.class)));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatus());
        server.verify();
    }

    @Test
    @DisplayName("일시적 네트워크 오류는 재시도 후 성공하면 정상 반환한다")
    void execute_retriesOnTransientNetworkError_thenSucceeds() {
        server.expect(requestTo(URI)).andRespond(request -> {
            throw new IOException("connection reset");
        });
        server.expect(requestTo(URI)).andRespond(withSuccess("{\"price\":10000}", MediaType.APPLICATION_JSON));

        PriceDto result = client.execute(
                () -> restClient.get().uri("/api/v3/simple/price").retrieve().body(PriceDto.class));

        assertEquals(10000, result.price());
        server.verify();
    }

    private record PriceDto(int price) {}
}
