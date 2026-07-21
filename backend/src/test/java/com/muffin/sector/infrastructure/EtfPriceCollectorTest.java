package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.exception.SectorErrorCode;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EtfPriceCollectorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);

    @Mock
    private EtfRepository etfRepository;

    @Mock
    private TossMarketDataClient tossMarketDataClient;

    @Mock
    private TradingCalendarService tradingCalendarService;

    @Mock
    private EtfPriceWriter etfPriceWriter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EtfPriceRepository etfPriceRepository;

    private EtfPriceCollector collector;

    @BeforeEach
    void setUp() {
        collector = new EtfPriceCollector(
                etfRepository,
                tradingCalendarService,
                tossMarketDataClient,
                etfPriceWriter,
                eventPublisher,
                etfPriceRepository);
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(tradingDay());
    }

    @Test
    @DisplayName("CoinGecko가 담당하는 BTC는 시딩되어 있어도 토스 API로 조회하지 않는다")
    void collect_excludesBtc() {
        Etf toss = Etf.create("459580", "KODEX CD금리액티브(합성)");
        Etf btc = Etf.create("BTC", "비트코인");
        when(etfRepository.findAll()).thenReturn(List.of(toss, btc));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T09:05:00+09:00", "10000", "10600", "9900", "10500", "12345", "KRW")));

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(1, summary.successCount());
        verify(tossMarketDataClient, never()).getDailyCandle(eq("BTC"), any());
        verify(etfPriceWriter).writeOpen(any(), eq(DATE), eq(10_000L));
    }

    @Test
    @DisplayName("collectOpen은 시가만 기록하고 종가는 건드리지 않는다")
    void collectOpen_writesOnlyOpenPrice() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T09:05:00+09:00", "10000", "10600", "9900", "10500", "12345", "KRW")));

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(1, summary.successCount());
        assertEquals(0, summary.skippedCount());
        assertEquals(0, summary.failureCount());
        verify(etfPriceWriter).writeOpen(any(), eq(DATE), eq(10_000L));
        verify(etfPriceWriter, never()).writeClose(any(), any(), any());
        verify(eventPublisher).publishEvent(new EtfPricesLoadedEvent(DATE));
    }

    @Test
    @DisplayName("collectClose는 종가만 기록하고 시가는 건드리지 않는다")
    void collectClose_writesOnlyClosePrice() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T15:30:00+09:00", "10000", "10600", "9900", "10500", "12345", "KRW")));

        EtfPriceCollector.CollectionSummary summary = collector.collectClose(DATE);

        assertEquals(1, summary.successCount());
        verify(etfPriceWriter).writeClose(any(), eq(DATE), eq(10_500L));
        verify(etfPriceWriter, never()).writeOpen(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("종가 재시도는 이미 성공한 종목을 외부 API로 다시 조회하지 않는다")
    void collectClose_skipsAlreadySuccessfulPrice() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        ReflectionTestUtils.setField(etf, "id", 1L);
        EtfPrice price = EtfPrice.create(1L, DATE, null, 10_500L);
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(etfPriceRepository.findByEtfIdAndPriceDate(1L, DATE)).thenReturn(Optional.of(price));

        EtfPriceCollector.CollectionSummary summary = collector.collectClose(DATE);

        assertEquals(1, summary.successCount());
        verify(tossMarketDataClient, never()).getDailyCandle(any(), any());
        verify(etfPriceWriter, never()).writeClose(any(), any(), any());
    }

    @Test
    @DisplayName("거래일이 아니면 전체를 skip하고 캔들 조회 자체를 시도하지 않는다")
    void collect_skipsEverything_whenNotTradingDay() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(nonTradingDay());

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(0, summary.successCount());
        assertEquals(1, summary.skippedCount());
        assertEquals(0, summary.failureCount());
        assertEquals(List.of("459580"), summary.skippedEtfCodes());
        verify(tossMarketDataClient, never()).getDailyCandle(any(), any());
        verify(etfPriceWriter).markOpenMarketClosed(any(), eq(DATE));
        verify(etfPriceWriter, never()).writeOpen(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("거래일을 확인할 수 없으면 가격 상태를 변경하지 않고 실행을 중단한다")
    void collect_aborts_whenMarketCalendarFails() {
        when(tradingCalendarService.getCalendar(DATE))
                .thenThrow(new GeneralException(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE));

        assertThrows(GeneralException.class, () -> collector.collectOpen(DATE));

        verify(etfRepository, never()).findAll();
        verify(etfPriceWriter, never()).markOpenFailed(any(), any());
        verify(tossMarketDataClient, never()).getDailyCandle(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("거래일인데 특정 ETF만 캔들이 없으면 NO_DATA로 기록하고 skip으로 집계한다")
    void collect_skipsSingleEtf_whenTradingDayButNoCandle() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tossMarketDataClient.getDailyCandle("459580", DATE)).thenReturn(Optional.empty());

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(0, summary.successCount());
        assertEquals(1, summary.skippedCount());
        assertEquals(0, summary.failureCount());
        assertEquals(List.of("459580"), summary.skippedEtfCodes());
        verify(etfPriceWriter).markOpenNoData(any(), eq(DATE));
        verify(etfPriceWriter, never()).writeOpen(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("가격 값을 파싱할 수 없으면 실패로 집계한다")
    void collect_countsAsFailure_whenPriceUnparseable() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T09:05:00+09:00", "N/A", "10600", "9900", "10500", "12345", "KRW")));

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(0, summary.successCount());
        assertEquals(0, summary.skippedCount());
        assertEquals(1, summary.failureCount());
        assertEquals(List.of("459580"), summary.failedEtfCodes());
        verify(etfPriceWriter).markOpenFailed(any(), eq(DATE));
        verify(etfPriceWriter, never()).writeOpen(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-100"})
    @DisplayName("0 이하의 시세는 저장하지 않고 실패로 집계한다")
    void collect_countsAsFailure_whenPriceIsNotPositive(String rawPrice) {
        Etf etf = Etf.create("459580", "KODEX ETF");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T09:05:00+09:00", rawPrice, "10600", "9900", "10500", "12345", "KRW")));

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(0, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(List.of("459580"), summary.failedEtfCodes());
        verify(etfPriceWriter).markOpenFailed(any(), eq(DATE));
        verify(etfPriceWriter, never()).writeOpen(any(), any(), any());
    }

    @Test
    @DisplayName("저장 단계에서 예외가 나도 해당 ETF만 실패로 집계하고 나머지는 계속 진행한다")
    void collect_isolatesWriterFailure() {
        Etf failing = Etf.create("999999", "저장실패종목");
        Etf succeeding = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(failing, succeeding));
        when(tossMarketDataClient.getDailyCandle("999999", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T09:05:00+09:00", "10000", "10600", "9900", "10500", "12345", "KRW")));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T09:05:00+09:00", "20000", "20600", "19900", "20500", "12345", "KRW")));
        doThrow(new DataIntegrityViolationException("uk_etf_price_etf_price_date"))
                .when(etfPriceWriter)
                .writeOpen(any(), eq(DATE), eq(10_000L));

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(1, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(List.of("999999"), summary.failedEtfCodes());
        verify(etfPriceWriter).markOpenFailed(any(), eq(DATE));
        verify(etfPriceWriter).writeOpen(any(), eq(DATE), eq(20_000L));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("한 ETF가 API 실패해도 나머지는 계속 수집하고 실패 목록에 남긴다")
    void collect_continuesAfterOneEtfFails() {
        Etf failing = Etf.create("999999", "실패종목");
        Etf succeeding = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(failing, succeeding));
        when(tossMarketDataClient.getDailyCandle("999999", DATE))
                .thenThrow(new TossApiException("r1", "NOT_FOUND", null, "종목을 찾을 수 없습니다"));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(Optional.of(
                        new Candle("2026-07-10T09:05:00+09:00", "10000", "10600", "9900", "10500", "12345", "KRW")));

        EtfPriceCollector.CollectionSummary summary = collector.collectOpen(DATE);

        assertEquals(1, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(List.of("999999"), summary.failedEtfCodes());
        verify(etfPriceWriter).markOpenFailed(any(), eq(DATE));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static TradingCalendar tradingDay() {
        return new TradingCalendar(DATE, true, DATE.minusDays(1), DATE.plusDays(3));
    }

    private static TradingCalendar nonTradingDay() {
        return new TradingCalendar(DATE, false, DATE.minusDays(1), DATE.plusDays(3));
    }
}
