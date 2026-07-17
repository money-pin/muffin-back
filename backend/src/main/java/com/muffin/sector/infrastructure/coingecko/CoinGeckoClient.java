package com.muffin.sector.infrastructure.coingecko;

import com.muffin.sector.infrastructure.coingecko.exception.CoinGeckoApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** CoinGecko API에서 코인 원화 시세를 가져온다. 응답을 정산에 쓰는 방식(§코인 섹터 기준가 정책)은 이 클래스의 책임이 아니다. */
@Component
public class CoinGeckoClient {

    private static final String SIMPLE_PRICE_PATH = "/api/v3/simple/price";
    private static final String BITCOIN_ID = "bitcoin";
    private static final String KRW_CURRENCY = "krw";
    // 쿼리 파라미터로 전달하면 프록시·접근 로그·APM에 키가 그대로 남을 수 있어 헤더로 전달한다.
    private static final String API_KEY_HEADER = "x-cg-demo-api-key";
    private static final ParameterizedTypeReference<Map<String, Map<String, BigDecimal>>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient coinGeckoRestClient;
    private final CoinGeckoApiClient coinGeckoApiClient;
    private final CoinGeckoApiProperties properties;

    public CoinGeckoClient(
            RestClient coinGeckoRestClient, CoinGeckoApiClient coinGeckoApiClient, CoinGeckoApiProperties properties) {
        this.coinGeckoRestClient = coinGeckoRestClient;
        this.coinGeckoApiClient = coinGeckoApiClient;
        this.properties = properties;
    }

    /** 서비스 기준 시각(코인 섹터 기준가 정책 §09:00 KST)에 호출해 비트코인의 현재 원화가를 정수 원 단위로 반환한다. */
    public Long getBitcoinPriceKrw() {
        Map<String, Map<String, BigDecimal>> response = coinGeckoApiClient.execute(() -> coinGeckoRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(SIMPLE_PRICE_PATH)
                        .queryParam("ids", BITCOIN_ID)
                        .queryParam("vs_currencies", KRW_CURRENCY)
                        .build())
                .header(API_KEY_HEADER, properties.apiKey())
                .retrieve()
                .body(RESPONSE_TYPE));

        return toWon(extractPrice(response));
    }

    private BigDecimal extractPrice(Map<String, Map<String, BigDecimal>> response) {
        if (response == null) {
            throw invalidResponse();
        }
        Map<String, BigDecimal> priceByCurrency = response.get(BITCOIN_ID);
        if (priceByCurrency == null) {
            throw invalidResponse();
        }
        BigDecimal price = priceByCurrency.get(KRW_CURRENCY);
        if (price == null) {
            throw invalidResponse();
        }
        return price;
    }

    private Long toWon(BigDecimal price) {
        try {
            return price.setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            throw invalidResponse();
        }
    }

    private CoinGeckoApiException invalidResponse() {
        return new CoinGeckoApiException(null, "CoinGecko API의 시세 응답 형식이 올바르지 않습니다.");
    }
}
