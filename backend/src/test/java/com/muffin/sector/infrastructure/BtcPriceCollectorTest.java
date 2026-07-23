package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.exception.SectorErrorCode;
import com.muffin.sector.infrastructure.coingecko.CoinGeckoClient;
import com.muffin.sector.infrastructure.coingecko.exception.CoinGeckoApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BtcPriceCollectorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);
    private static final Long BTC_ID = 42L;

    @Mock
    private EtfRepository etfRepository;

    @Mock
    private TradingCalendarService tradingCalendarService;

    @Mock
    private CoinGeckoClient coinGeckoClient;

    @Mock
    private EtfPriceWriter etfPriceWriter;

    @Mock
    private EtfPriceRepository etfPriceRepository;

    private BtcPriceCollector collector;

    @BeforeEach
    void setUp() {
        collector = new BtcPriceCollector(
                etfRepository, tradingCalendarService, coinGeckoClient, etfPriceWriter, etfPriceRepository);
    }

    @Test
    @DisplayName("거래일이면 코인 시세를 조회해 기준가로 저장하고 완료 이벤트를 발행한다")
    void collect_writesBasePrice_onTradingDay() {
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(tradingDay());
        when(etfRepository.findByEtfCode("BTC")).thenReturn(Optional.of(btc()));
        when(coinGeckoClient.getBitcoinPriceKrw()).thenReturn(123_456_789L);

        collector.collect(DATE);

        verify(etfPriceWriter).writeBasePrice(BTC_ID, DATE, 123_456_789L);
    }

    @Test
    @DisplayName("거래일이 아니면 API 호출 없이 MARKET_CLOSED로 기록한다")
    void collect_marksMarketClosed_whenNotTradingDay() {
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(nonTradingDay());
        when(etfRepository.findByEtfCode("BTC")).thenReturn(Optional.of(btc()));

        collector.collect(DATE);

        verify(etfPriceWriter).markBaseMarketClosed(BTC_ID, DATE);
        verify(coinGeckoClient, never()).getBitcoinPriceKrw();
    }

    @Test
    @DisplayName("BTC 기준 데이터가 없으면 아무 것도 하지 않는다")
    void collect_doesNothing_whenBtcEtfMissing() {
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(tradingDay());
        when(etfRepository.findByEtfCode("BTC")).thenReturn(Optional.empty());

        collector.collect(DATE);

        verify(coinGeckoClient, never()).getBitcoinPriceKrw();
        verify(etfPriceWriter, never()).markBaseMarketClosed(any(), any());
        verify(etfPriceWriter, never()).markBaseFailed(any(), any());
    }

    @Test
    @DisplayName("거래일을 확인할 수 없으면 가격 상태를 변경하지 않고 실행을 중단한다")
    void collect_aborts_whenMarketCalendarFails() {
        when(tradingCalendarService.getCalendar(DATE))
                .thenThrow(new GeneralException(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE));

        assertThrows(GeneralException.class, () -> collector.collect(DATE));

        verify(etfRepository, never()).findByEtfCode(any());
        verify(etfPriceWriter, never()).markBaseFailed(any(), any());
    }

    @Test
    @DisplayName("코인 시세 조회에 실패하면 FAILED로 기록하고 이벤트를 발행하지 않는다")
    void collect_marksFailed_whenCoinGeckoCallFails() {
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(tradingDay());
        when(etfRepository.findByEtfCode("BTC")).thenReturn(Optional.of(btc()));
        when(coinGeckoClient.getBitcoinPriceKrw()).thenThrow(new CoinGeckoApiException(null, "provider failure"));

        collector.collect(DATE);

        verify(etfPriceWriter).markBaseFailed(BTC_ID, DATE);
        verify(etfPriceWriter, never()).writeBasePrice(any(), any(), any());
    }

    @Test
    @DisplayName("저장 단계에서 예외가 나도 FAILED로 기록하고 이벤트를 발행하지 않는다")
    void collect_marksFailed_whenWriteFails() {
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(tradingDay());
        when(etfRepository.findByEtfCode("BTC")).thenReturn(Optional.of(btc()));
        when(coinGeckoClient.getBitcoinPriceKrw()).thenReturn(123_456_789L);
        doThrow(new RuntimeException("db error")).when(etfPriceWriter).writeBasePrice(eq(BTC_ID), eq(DATE), any());

        collector.collect(DATE);

        verify(etfPriceWriter).markBaseFailed(BTC_ID, DATE);
    }

    @Test
    @DisplayName("이미 기준가 수집에 성공한 BTC는 외부 API를 다시 호출하지 않는다")
    void collect_skipsAlreadySuccessfulBasePrice() {
        when(tradingCalendarService.getCalendar(DATE)).thenReturn(tradingDay());
        when(etfRepository.findByEtfCode("BTC")).thenReturn(Optional.of(btc()));
        when(etfPriceRepository.findByEtfIdAndPriceDate(BTC_ID, DATE))
                .thenReturn(Optional.of(EtfPrice.create(BTC_ID, DATE, 123_456_789L, 123_456_789L)));

        collector.collect(DATE);

        verify(coinGeckoClient, never()).getBitcoinPriceKrw();
        verify(etfPriceWriter, never()).writeBasePrice(any(), any(), any());
    }

    private static Etf btc() {
        Etf etf = Etf.create("BTC", "비트코인");
        ReflectionTestUtils.setField(etf, "id", BTC_ID);
        return etf;
    }

    private static TradingCalendar tradingDay() {
        return new TradingCalendar(DATE, true, DATE.minusDays(1), DATE.plusDays(3));
    }

    private static TradingCalendar nonTradingDay() {
        return new TradingCalendar(DATE, false, DATE.minusDays(1), DATE.plusDays(3));
    }
}
