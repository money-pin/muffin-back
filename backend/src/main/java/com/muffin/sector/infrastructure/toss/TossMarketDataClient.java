package com.muffin.sector.infrastructure.toss;

import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 토스증권 API에서 일봉/거래일 원본 데이터를 가져온다. 응답을 해석하는 비즈니스 로직(거래일 판정 등, §5.2)은 이 클래스의 책임이 아니며,
 * 별도 서비스가 담당한다.
 */
@Component
public class TossMarketDataClient {

    private static final String CANDLES_PATH = "/api/v1/candles";
    private static final String MARKET_CALENDAR_PATH = "/api/v1/market-calendar/KR";

    private final RestClient tossRestClient;
    private final TossApiClient tossApiClient;
    private final TossTokenProvider tossTokenProvider;

    public TossMarketDataClient(
            RestClient tossRestClient, TossApiClient tossApiClient, TossTokenProvider tossTokenProvider) {
        this.tossRestClient = tossRestClient;
        this.tossApiClient = tossApiClient;
        this.tossTokenProvider = tossTokenProvider;
    }

    /** 특정 종목의 특정 일자 일봉을 조회한다. */
    public TossCandleResponse getDailyCandle(String etfCode, LocalDate date) {
        return tossApiClient.execute(() -> tossRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(CANDLES_PATH)
                        .queryParam("symbol", etfCode)
                        .queryParam("date", date)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(TossCandleResponse.class));
    }

    /** 특정 일자가 국내 거래일인지 여부를 조회한다. */
    public TossMarketCalendarResponse getMarketCalendar(LocalDate date) {
        return tossApiClient.execute(() -> tossRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(MARKET_CALENDAR_PATH)
                        .queryParam("date", date)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(TossMarketCalendarResponse.class));
    }

    private String bearerToken() {
        return "Bearer " + tossTokenProvider.getAccessToken();
    }
}
