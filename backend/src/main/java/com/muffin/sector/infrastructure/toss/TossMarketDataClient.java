package com.muffin.sector.infrastructure.toss;

import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
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
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String DAILY_INTERVAL = "1d";

    private final RestClient tossRestClient;
    private final TossApiClient tossApiClient;
    private final TossTokenProvider tossTokenProvider;

    public TossMarketDataClient(
            RestClient tossRestClient, TossApiClient tossApiClient, TossTokenProvider tossTokenProvider) {
        this.tossRestClient = tossRestClient;
        this.tossApiClient = tossApiClient;
        this.tossTokenProvider = tossTokenProvider;
    }

    /**
     * 특정 종목의 특정 일자 일봉을 조회한다. 캔들 API는 날짜 단건 조회가 아니라 {@code before} 커서 기반 페이지네이션만
     * 지원하므로, 대상 일자의 마지막 순간을 커서로 1건만 요청한 뒤 실제로 그 날짜인지 확인한다. 거래일이 아니면 비어있다.
     */
    public Optional<Candle> getDailyCandle(String etfCode, LocalDate date) {
        String before =
                date.plusDays(1).atStartOfDay(KST).minusSeconds(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        TossCandleResponse response = tossApiClient.execute(() -> tossRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(CANDLES_PATH)
                        .queryParam("symbol", etfCode)
                        .queryParam("interval", DAILY_INTERVAL)
                        .queryParam("count", 1)
                        .queryParam("before", before)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(TossCandleResponse.class));

        return candleOn(response, date);
    }

    /** 특정 일자가 국내 거래일인지 여부를 조회한다. */
    public TossMarketCalendarResponse.Result getMarketCalendar(LocalDate date) {
        TossMarketCalendarResponse response = tossApiClient.execute(() -> tossRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(MARKET_CALENDAR_PATH)
                        .queryParam("date", date)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerToken())
                .retrieve()
                .body(TossMarketCalendarResponse.class));

        if (response == null || response.result() == null) {
            throw invalidResponse("거래일");
        }
        return response.result();
    }

    private Optional<Candle> candleOn(TossCandleResponse response, LocalDate date) {
        if (response == null || response.result() == null || response.result().candles() == null) {
            throw invalidResponse("일봉");
        }
        List<Candle> candles = response.result().candles();
        return candles.stream().filter(candle -> isOnDate(candle, date)).findFirst();
    }

    private TossApiException invalidResponse(String responseType) {
        return new TossApiException(null, "INVALID_RESPONSE", null, "토스증권 API의 " + responseType + " 응답 형식이 올바르지 않습니다.");
    }

    private boolean isOnDate(Candle candle, LocalDate date) {
        if (candle == null || candle.timestamp() == null) {
            return false;
        }
        try {
            return OffsetDateTime.parse(candle.timestamp())
                    .atZoneSameInstant(KST)
                    .toLocalDate()
                    .equals(date);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private String bearerToken() {
        return "Bearer " + tossTokenProvider.getAccessToken();
    }
}
