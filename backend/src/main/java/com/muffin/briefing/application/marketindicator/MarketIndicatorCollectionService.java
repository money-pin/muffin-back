package com.muffin.briefing.application.marketindicator;

import com.muffin.briefing.domain.MarketIndicatorPrice;
import com.muffin.briefing.domain.MarketIndicatorPriceRepository;
import com.muffin.briefing.domain.MarketIndicatorStatus;
import com.muffin.briefing.domain.enums.MarketIndicator;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.dto.TossExchangeRateResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 간밤의 시장 지표를 수집한다. (SYS-08)
 *
 * <p>지표마다 조회 경로가 다르다. 국내 지수는 토스 지표 카탈로그에 있어 지표 엔드포인트로 받고, 해외 지수는 카탈로그에 없어 추종 ETF를
 * 개별 종목 캔들로 받고, 환율은 전용 엔드포인트를 쓴다.
 *
 * <p>지표 하나의 수집 실패가 나머지를 막지 않는다. 기획서의 "일부 지표 수신 실패 시 해당 카드 수치만 비우고 나머지는 정상 노출"을
 * 그대로 구현한 것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndicatorCollectionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 등락률 계산에 당일과 직전 거래일 두 개가 필요하다. */
    private static final int REQUIRED_CANDLE_COUNT = 2;

    private static final String BASE_CURRENCY = "USD";
    private static final String QUOTE_CURRENCY = "KRW";

    private final TossMarketDataClient tossMarketDataClient;
    private final MarketIndicatorPriceRepository repository;

    /**
     * 모든 지표를 수집한다.
     *
     * @param baseDate 수집 기준일. 환율의 기록 일자로 쓰이며, 캔들은 응답의 자체 일자를 쓴다
     * @return 지표별 수집 결과
     */
    @Transactional
    public MarketIndicatorCollectionSummary collect(LocalDate baseDate) {
        int succeeded = 0;
        int failed = 0;

        for (MarketIndicator indicator : MarketIndicator.values()) {
            try {
                Collected collected = fetch(indicator, baseDate);
                if (collected == null) {
                    saveUnavailable(indicator, baseDate, MarketIndicatorStatus.NO_DATA);
                    failed++;
                    log.warn("Market indicator has no data: indicator={} baseDate={}", indicator, baseDate);
                    continue;
                }
                save(indicator, collected);
                succeeded++;
            } catch (Exception exception) {
                saveUnavailableSafely(indicator, baseDate);
                failed++;
                log.error(
                        "Market indicator collection failed: indicator={} baseDate={}", indicator, baseDate, exception);
            }
        }

        log.info(
                "Market indicator collection finished: baseDate={} succeeded={} failed={}",
                baseDate,
                succeeded,
                failed);
        return new MarketIndicatorCollectionSummary(succeeded, failed);
    }

    private Collected fetch(MarketIndicator indicator, LocalDate baseDate) {
        return switch (indicator.source()) {
            case INDICATOR ->
                fromCandles(
                        tossMarketDataClient.getIndicatorDailyCandles(indicator.tossSymbol(), REQUIRED_CANDLE_COUNT));
            case STOCK ->
                fromCandles(tossMarketDataClient.getDailyCandles(indicator.tossSymbol(), REQUIRED_CANDLE_COUNT));
            case EXCHANGE_RATE -> fromExchangeRate(baseDate);
        };
    }

    /** 캔들은 최신순으로 오므로 첫 요소가 당일, 두 번째가 직전 거래일이다. */
    private Collected fromCandles(List<Candle> candles) {
        if (candles.isEmpty()) {
            return null;
        }
        Candle latest = candles.getFirst();
        BigDecimal closePrice = parsePrice(latest.closePrice());
        if (closePrice == null) {
            return null;
        }
        BigDecimal previousClose =
                candles.size() > 1 ? parsePrice(candles.get(1).closePrice()) : null;
        return new Collected(candleDate(latest), closePrice, previousClose);
    }

    /**
     * 환율은 캔들이 없어 현재 시점과 24시간 전 시점을 각각 조회해 등락률을 만든다. 외환은 공식 종가 개념이 없으므로 "같은 시각 기준
     * 하루 변화"로 정의한다.
     */
    private Collected fromExchangeRate(LocalDate baseDate) {
        TossExchangeRateResponse.Result current =
                tossMarketDataClient.getExchangeRate(BASE_CURRENCY, QUOTE_CURRENCY, null);
        BigDecimal rate = parsePrice(current.rate());
        if (rate == null) {
            return null;
        }

        BigDecimal previousRate = null;
        try {
            OffsetDateTime dayBefore = OffsetDateTime.now(KST).minusDays(1);
            previousRate = parsePrice(tossMarketDataClient
                    .getExchangeRate(BASE_CURRENCY, QUOTE_CURRENCY, dayBefore)
                    .rate());
        } catch (Exception exception) {
            // 직전 환율을 못 받으면 등락률 없이 현재 환율만 남긴다. 카드가 비는 것보다 낫다.
            log.warn("Previous exchange rate unavailable, storing rate without change", exception);
        }
        return new Collected(baseDate, rate, previousRate);
    }

    private void save(MarketIndicator indicator, Collected collected) {
        repository
                .findByIndicatorAndPriceDate(indicator, collected.priceDate())
                .ifPresentOrElse(
                        existing -> existing.update(collected.closePrice(), collected.previousClose()),
                        () -> repository.save(MarketIndicatorPrice.success(
                                indicator, collected.priceDate(), collected.closePrice(), collected.previousClose())));
    }

    private void saveUnavailable(MarketIndicator indicator, LocalDate priceDate, MarketIndicatorStatus status) {
        repository
                .findByIndicatorAndPriceDate(indicator, priceDate)
                .ifPresentOrElse(
                        existing -> existing.markUnavailable(status),
                        () -> repository.save(MarketIndicatorPrice.unavailable(indicator, priceDate, status)));
    }

    /** 실패 기록까지 실패하면 다음 수집이 처리하므로 예외를 밖으로 던지지 않는다. */
    private void saveUnavailableSafely(MarketIndicator indicator, LocalDate priceDate) {
        try {
            saveUnavailable(indicator, priceDate, MarketIndicatorStatus.FAILED);
        } catch (Exception exception) {
            log.error("Failed to record market indicator failure: indicator={}", indicator, exception);
        }
    }

    private static LocalDate candleDate(Candle candle) {
        return OffsetDateTime.parse(candle.timestamp()).atZoneSameInstant(KST).toLocalDate();
    }

    private static BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record Collected(LocalDate priceDate, BigDecimal closePrice, BigDecimal previousClose) {}
}
