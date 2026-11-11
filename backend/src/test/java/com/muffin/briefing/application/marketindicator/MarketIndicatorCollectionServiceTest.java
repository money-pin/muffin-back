package com.muffin.briefing.application.marketindicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.briefing.domain.MarketIndicatorPrice;
import com.muffin.briefing.domain.MarketIndicatorPriceRepository;
import com.muffin.briefing.domain.MarketIndicatorStatus;
import com.muffin.briefing.domain.enums.MarketIndicator;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.dto.TossExchangeRateResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketIndicatorCollectionServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 10, 7);

    private TossMarketDataClient tossMarketDataClient;
    private MarketIndicatorPriceRepository repository;
    private MarketIndicatorCollectionService service;
    private final List<MarketIndicatorPrice> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tossMarketDataClient = mock(TossMarketDataClient.class);
        repository = mock(MarketIndicatorPriceRepository.class);
        saved.clear();

        when(repository.findByIndicatorAndPriceDate(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(MarketIndicatorPrice.class))).thenAnswer(invocation -> {
            MarketIndicatorPrice price = invocation.getArgument(0);
            saved.add(price);
            return price;
        });

        service = new MarketIndicatorCollectionService(tossMarketDataClient, repository);
    }

    @Test
    @DisplayName("국내 지수는 지표 엔드포인트로, 해외 지수는 추종 ETF 종목으로 조회한다")
    void collect_usesIndicatorEndpointForDomesticAndStockEndpointForOverseas() {
        givenAllIndicators();

        service.collect(BASE_DATE);

        verify(tossMarketDataClient).getIndicatorDailyCandles(eq("KOSPI"), anyInt());
        verify(tossMarketDataClient).getIndicatorDailyCandles(eq("KOSDAQ"), anyInt());
        verify(tossMarketDataClient).getDailyCandles(eq("SPY"), anyInt());
        verify(tossMarketDataClient).getDailyCandles(eq("QQQ"), anyInt());
    }

    @Test
    @DisplayName("당일과 직전 종가로 등락률을 계산해 저장한다")
    void collect_computesChangeRateFromTwoCandles() {
        givenAllIndicators();

        MarketIndicatorCollectionSummary summary = service.collect(BASE_DATE);

        assertThat(summary.succeeded()).isEqualTo(MarketIndicator.values().length);
        assertThat(summary.failed()).isZero();
        // 2798.10 → 2812.45 이면 +0.51%
        MarketIndicatorPrice kospi = savedOf(MarketIndicator.KOSPI);
        assertThat(kospi.getClosePrice()).isEqualByComparingTo(new BigDecimal("2812.45"));
        assertThat(kospi.getChangeRate()).isEqualByComparingTo(new BigDecimal("0.51"));
    }

    /** 한 지표의 실패가 나머지를 막으면 블록이 통째로 빈다. */
    @Test
    @DisplayName("한 지표가 실패해도 나머지는 수집하고 실패한 지표만 상태로 남긴다")
    void collect_isolatesFailurePerIndicator() {
        givenAllIndicators();
        when(tossMarketDataClient.getDailyCandles(eq("QQQ"), anyInt()))
                .thenThrow(new IllegalStateException("toss down"));

        MarketIndicatorCollectionSummary summary = service.collect(BASE_DATE);

        assertThat(summary.succeeded()).isEqualTo(MarketIndicator.values().length - 1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(savedOf(MarketIndicator.NASDAQ100).getStatus()).isEqualTo(MarketIndicatorStatus.FAILED);
        assertThat(savedOf(MarketIndicator.KOSPI).isUsable()).isTrue();
    }

    @Test
    @DisplayName("봉이 없으면 데이터 없음으로 남긴다")
    void collect_marksNoDataWhenCandlesAreEmpty() {
        givenAllIndicators();
        when(tossMarketDataClient.getIndicatorDailyCandles(eq("KOSDAQ"), anyInt()))
                .thenReturn(List.of());

        service.collect(BASE_DATE);

        assertThat(savedOf(MarketIndicator.KOSDAQ).getStatus()).isEqualTo(MarketIndicatorStatus.NO_DATA);
    }

    @Test
    @DisplayName("봉이 하나뿐이면 등락률 없이 종가만 남긴다")
    void collect_storesCloseWithoutChangeRateWhenOnlyOneCandle() {
        givenAllIndicators();
        when(tossMarketDataClient.getIndicatorDailyCandles(eq("KOSPI"), anyInt()))
                .thenReturn(List.of(candle("2026-10-07T09:00:00+09:00", "2812.45")));

        service.collect(BASE_DATE);

        assertThat(savedOf(MarketIndicator.KOSPI).getClosePrice()).isEqualByComparingTo(new BigDecimal("2812.45"));
        assertThat(savedOf(MarketIndicator.KOSPI).getChangeRate()).isNull();
    }

    /** 외환은 공식 종가가 없어 현재와 24시간 전을 비교한다. 과거 환율을 못 받아도 현재 환율은 살린다. */
    @Test
    @DisplayName("직전 환율을 못 받으면 등락률 없이 현재 환율만 남긴다")
    void collect_storesExchangeRateWithoutChangeWhenPreviousIsUnavailable() {
        givenAllIndicators();
        // dateTime이 null인 호출은 현재 환율, null이 아닌 호출은 24시간 전 환율이다. 과거 조회만 실패시킨다.
        when(tossMarketDataClient.getExchangeRate(eq("USD"), eq("KRW"), notNull()))
                .thenThrow(new IllegalStateException("no history"));

        service.collect(BASE_DATE);

        MarketIndicatorPrice usdKrw = savedOf(MarketIndicator.USD_KRW);
        assertThat(usdKrw.getClosePrice()).isEqualByComparingTo(new BigDecimal("1380.5"));
        assertThat(usdKrw.getChangeRate()).isNull();
    }

    @Test
    @DisplayName("같은 날 다시 수집하면 새 행을 만들지 않고 기존 행을 갱신한다")
    void collect_updatesExistingRowInsteadOfInserting() {
        givenAllIndicators();
        MarketIndicatorPrice existing = MarketIndicatorPrice.unavailable(
                MarketIndicator.KOSPI, LocalDate.of(2026, 10, 7), MarketIndicatorStatus.FAILED);
        when(repository.findByIndicatorAndPriceDate(MarketIndicator.KOSPI, LocalDate.of(2026, 10, 7)))
                .thenReturn(Optional.of(existing));

        service.collect(BASE_DATE);

        assertThat(existing.getStatus()).isEqualTo(MarketIndicatorStatus.SUCCESS);
        assertThat(existing.getClosePrice()).isEqualByComparingTo(new BigDecimal("2812.45"));
        ArgumentCaptor<MarketIndicatorPrice> captor = ArgumentCaptor.forClass(MarketIndicatorPrice.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(price -> price.getIndicator() == MarketIndicator.KOSPI);
    }

    private void givenAllIndicators() {
        when(tossMarketDataClient.getIndicatorDailyCandles(eq("KOSPI"), anyInt()))
                .thenReturn(List.of(
                        candle("2026-10-07T09:00:00+09:00", "2812.45"),
                        candle("2026-10-06T09:00:00+09:00", "2798.10")));
        when(tossMarketDataClient.getIndicatorDailyCandles(eq("KOSDAQ"), anyInt()))
                .thenReturn(List.of(
                        candle("2026-10-07T09:00:00+09:00", "870.20"), candle("2026-10-06T09:00:00+09:00", "875.00")));
        when(tossMarketDataClient.getDailyCandles(eq("SPY"), anyInt()))
                .thenReturn(List.of(
                        candle("2026-10-07T00:00:00+09:00", "612.40"), candle("2026-10-06T00:00:00+09:00", "608.10")));
        when(tossMarketDataClient.getDailyCandles(eq("QQQ"), anyInt()))
                .thenReturn(List.of(
                        candle("2026-10-07T00:00:00+09:00", "512.30"), candle("2026-10-06T00:00:00+09:00", "518.00")));
        when(tossMarketDataClient.getExchangeRate(eq("USD"), eq("KRW"), any())).thenReturn(exchangeRate("1380.5"));
    }

    private MarketIndicatorPrice savedOf(MarketIndicator indicator) {
        return saved.stream()
                .filter(price -> price.getIndicator() == indicator)
                .findFirst()
                .orElseThrow(() -> new AssertionError("saved price not found: " + indicator));
    }

    private static Candle candle(String timestamp, String closePrice) {
        return new Candle(timestamp, closePrice, closePrice, closePrice, closePrice, "1000", "KRW");
    }

    private static TossExchangeRateResponse.Result exchangeRate(String rate) {
        return new TossExchangeRateResponse.Result("USD", "KRW", rate, rate, "40", "UP", null, null);
    }
}
