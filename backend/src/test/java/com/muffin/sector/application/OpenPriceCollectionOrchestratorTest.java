package com.muffin.sector.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;
import com.muffin.sector.infrastructure.BtcPriceCollector;
import com.muffin.sector.infrastructure.EtfPriceCollector;
import com.muffin.sector.infrastructure.EtfPriceWriter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpenPriceCollectionOrchestratorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 13);
    private static final Long BTC_ID = 1L;
    private static final Long TOSS_ID = 2L;

    @Mock
    private TradingCalendarService tradingCalendarService;

    @Mock
    private BtcPriceCollector btcPriceCollector;

    @Mock
    private EtfPriceCollector etfPriceCollector;

    @Mock
    private EtfRepository etfRepository;

    @Mock
    private EtfPriceRepository etfPriceRepository;

    @Mock
    private EtfPriceWriter etfPriceWriter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OpenPriceCollectionOrchestrator orchestrator;
    private List<Etf> targets;

    @BeforeEach
    void setUp() {
        orchestrator = new OpenPriceCollectionOrchestrator(
                tradingCalendarService,
                btcPriceCollector,
                etfPriceCollector,
                etfRepository,
                etfPriceRepository,
                etfPriceWriter,
                eventPublisher);
        targets = List.of(etf(BTC_ID, "BTC"), etf(TOSS_ID, "459580"));
    }

    @Test
    void collectOpenPrices_collectsSequentiallyAndPublishesWhenCompleted() {
        mockTradingDay();
        when(etfRepository.findAll()).thenReturn(targets);
        when(etfPriceRepository.findByPriceDate(DATE)).thenReturn(List.of()).thenReturn(completedPrices());

        orchestrator.collectOpenPrices(DATE);

        InOrder order = inOrder(btcPriceCollector, etfPriceCollector, eventPublisher);
        order.verify(btcPriceCollector).collect(DATE, true);
        order.verify(etfPriceCollector).collectOpen(DATE, true);
        order.verify(eventPublisher).publishEvent(new EtfPricesLoadedEvent(DATE));
        verify(tradingCalendarService).getCalendar(DATE);
    }

    @Test
    void collectOpenPrices_doesNotPublishWhenAnyTargetIsIncomplete() {
        mockTradingDay();
        when(etfRepository.findAll()).thenReturn(targets);
        when(etfPriceRepository.findByPriceDate(DATE))
                .thenReturn(List.of())
                .thenReturn(List.of(EtfPrice.create(BTC_ID, DATE, 50_000_000L, 50_000_000L)));

        orchestrator.collectOpenPrices(DATE);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void collectOpenPrices_doesNotRepeatCompletedDate() {
        mockTradingDay();
        when(etfRepository.findAll()).thenReturn(targets);
        when(etfPriceRepository.findByPriceDate(DATE)).thenReturn(completedPrices());

        orchestrator.collectOpenPrices(DATE);

        verify(btcPriceCollector, never()).collect(any(), anyBoolean());
        verify(etfPriceCollector, never()).collectOpen(any(), anyBoolean());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void collectOpenPrices_recordsMarketClosedWithoutPublishing() {
        when(tradingCalendarService.getCalendar(DATE))
                .thenReturn(new TradingCalendar(DATE, false, DATE.minusDays(3), DATE.plusDays(1)));

        orchestrator.collectOpenPrices(DATE);

        verify(etfRepository, never()).findAll();
        verify(btcPriceCollector).collect(DATE, false);
        verify(etfPriceCollector).collectOpen(DATE, false);
        verify(eventPublisher, never()).publishEvent(any());
        verify(tradingCalendarService).getCalendar(DATE);
    }

    @Test
    void collectOpenPrices_failsClosedWhenCalendarIsUnavailable() {
        when(tradingCalendarService.getCalendar(DATE))
                .thenThrow(new SectorException(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE));

        assertThrows(SectorException.class, () -> orchestrator.collectOpenPrices(DATE));

        verify(etfRepository, never()).findAll();
        verify(btcPriceCollector, never()).collect(any(), anyBoolean());
        verify(etfPriceCollector, never()).collectOpen(any(), anyBoolean());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void finalizeMissingOpenPrices_skipsMarketClosed() {
        when(tradingCalendarService.getCalendar(DATE))
                .thenReturn(new TradingCalendar(DATE, false, DATE.minusDays(3), DATE.plusDays(1)));

        orchestrator.finalizeMissingOpenPrices(DATE);

        verify(etfRepository, never()).findAll();
        verify(etfPriceWriter, never()).markOpenFinalMissing(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void finalizeMissingOpenPrices_marksOnlyIncompleteTargetsAndPublishes() {
        mockTradingDay();
        when(etfRepository.findAll()).thenReturn(targets);
        EtfPrice btc = EtfPrice.create(BTC_ID, DATE, 50_000_000L, 50_000_000L);
        EtfPrice tossFailed = EtfPrice.pending(TOSS_ID, DATE);
        tossFailed.markOpenFailed();
        EtfPrice tossFinalMissing = EtfPrice.pending(TOSS_ID, DATE);
        tossFinalMissing.markOpenFinalMissing();
        when(etfPriceRepository.findByPriceDate(DATE))
                .thenReturn(List.of(btc, tossFailed))
                .thenReturn(List.of(btc, tossFinalMissing));

        orchestrator.finalizeMissingOpenPrices(DATE);

        verify(etfPriceWriter, never()).markOpenFinalMissing(BTC_ID, DATE);
        verify(etfPriceWriter).markOpenFinalMissing(TOSS_ID, DATE);
        verify(eventPublisher).publishEvent(new EtfPricesLoadedEvent(DATE));
    }

    @Test
    void finalizeMissingOpenPrices_doesNotPublishWhenStatusSaveFails() {
        mockTradingDay();
        when(etfRepository.findAll()).thenReturn(targets);
        EtfPrice btc = EtfPrice.create(BTC_ID, DATE, 50_000_000L, 50_000_000L);
        EtfPrice tossFailed = EtfPrice.pending(TOSS_ID, DATE);
        tossFailed.markOpenFailed();
        when(etfPriceRepository.findByPriceDate(DATE))
                .thenReturn(List.of(btc, tossFailed))
                .thenReturn(List.of(btc, tossFailed));
        doThrow(new RuntimeException("db error")).when(etfPriceWriter).markOpenFinalMissing(TOSS_ID, DATE);

        orchestrator.finalizeMissingOpenPrices(DATE);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private void mockTradingDay() {
        when(tradingCalendarService.getCalendar(DATE))
                .thenReturn(new TradingCalendar(DATE, true, DATE.minusDays(3), DATE.plusDays(1)));
    }

    private List<EtfPrice> completedPrices() {
        return List.of(EtfPrice.create(BTC_ID, DATE, 50_000_000L, 50_000_000L), EtfPrice.open(TOSS_ID, DATE, 10_000L));
    }

    private Etf etf(Long id, String code) {
        Etf etf = Etf.create(code, code);
        ReflectionTestUtils.setField(etf, "id", id);
        return etf;
    }
}
