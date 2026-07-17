package com.muffin.sector.infrastructure.coingecko;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.sector.infrastructure.coingecko.exception.CoinGeckoApiException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoinGeckoClientTest {

    private static final String BASE_URL = "http://coingecko.test";
    private static final String URI_TEMPLATE = BASE_URL + "/api/v3/simple/price?ids={ids}&vs_currencies={vsCurrencies}";

    private MockRestServiceServer server;
    private CoinGeckoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        CoinGeckoApiProperties properties =
                new CoinGeckoApiProperties(BASE_URL, "test-api-key", Duration.ofSeconds(3), Duration.ofSeconds(5));
        client = new CoinGeckoClient(restClient, new CoinGeckoApiClient(), properties);
    }

    @Test
    @DisplayName("비트코인 원화 시세를 API 키 헤더와 함께 조회해 정수 원 단위로 반올림해 반환한다")
    void getBitcoinPriceKrw_returnsRoundedPrice() {
        server.expect(requestToUriTemplate(URI_TEMPLATE, "bitcoin", "krw"))
                .andExpect(header("x-cg-demo-api-key", "test-api-key"))
                .andRespond(withSuccess("{\"bitcoin\":{\"krw\":123456789.6}}", MediaType.APPLICATION_JSON));

        Long price = client.getBitcoinPriceKrw();

        assertEquals(123_456_790L, price);
        server.verify();
    }

    @Test
    @DisplayName("응답 본문이 없으면 명시적인 예외를 던진다")
    void getBitcoinPriceKrw_throwsException_whenBodyMissing() {
        server.expect(requestToUriTemplate(URI_TEMPLATE, "bitcoin", "krw")).andRespond(withSuccess());

        assertThrows(CoinGeckoApiException.class, () -> client.getBitcoinPriceKrw());
        server.verify();
    }

    @Test
    @DisplayName("응답에 bitcoin 키가 없으면 명시적인 예외를 던진다")
    void getBitcoinPriceKrw_throwsException_whenCoinMissing() {
        server.expect(requestToUriTemplate(URI_TEMPLATE, "bitcoin", "krw"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(CoinGeckoApiException.class, () -> client.getBitcoinPriceKrw());
        server.verify();
    }

    @Test
    @DisplayName("응답에 krw 시세가 없으면 명시적인 예외를 던진다")
    void getBitcoinPriceKrw_throwsException_whenCurrencyMissing() {
        server.expect(requestToUriTemplate(URI_TEMPLATE, "bitcoin", "krw"))
                .andRespond(withSuccess("{\"bitcoin\":{\"usd\":90000}}", MediaType.APPLICATION_JSON));

        assertThrows(CoinGeckoApiException.class, () -> client.getBitcoinPriceKrw());
        server.verify();
    }
}
