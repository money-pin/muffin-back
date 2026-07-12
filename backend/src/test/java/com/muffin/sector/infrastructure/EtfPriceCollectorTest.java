package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EtfPriceCollectorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);

    @Mock
    private EtfRepository etfRepository;

    @Mock
    private TossMarketDataClient tossMarketDataClient;

    @Mock
    private EtfPriceWriter etfPriceWriter;

    private EtfPriceCollector collector;

    @BeforeEach
    void setUp() {
        collector = new EtfPriceCollector(etfRepository, tossMarketDataClient, etfPriceWriter);
    }

    @Test
    @DisplayName("시가와 종가가 모두 있으면 둘 다 기록한다")
    void collect_writesOpenAndClose_whenBothPresent() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(new TossCandleResponse(DATE, 10_000L, 10_500L, 10_600L, 9_900L));

        EtfPriceCollector.CollectionSummary summary = collector.collect(DATE);

        assertEquals(1, summary.successCount());
        assertEquals(0, summary.failureCount());
        verify(etfPriceWriter).writeOpen(any(), eq(DATE), eq(10_000L));
        verify(etfPriceWriter).writeClose(any(), eq(DATE), eq(10_500L));
    }

    @Test
    @DisplayName("종가가 아직 없으면 시가만 기록한다")
    void collect_writesOpenOnly_whenCloseMissing() {
        Etf etf = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(etf));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(new TossCandleResponse(DATE, 10_000L, null, null, null));

        collector.collect(DATE);

        verify(etfPriceWriter).writeOpen(any(), eq(DATE), eq(10_000L));
        verify(etfPriceWriter, never()).writeClose(any(), any(), any());
    }

    @Test
    @DisplayName("한 ETF가 실패해도 나머지는 계속 수집하고 실패 목록에 남긴다")
    void collect_continuesAfterOneEtfFails() {
        Etf failing = Etf.create("999999", "실패종목");
        Etf succeeding = Etf.create("459580", "KODEX CD금리액티브(합성)");
        when(etfRepository.findAll()).thenReturn(List.of(failing, succeeding));
        when(tossMarketDataClient.getDailyCandle("999999", DATE))
                .thenThrow(new TossApiException("r1", "NOT_FOUND", null, "종목을 찾을 수 없습니다"));
        when(tossMarketDataClient.getDailyCandle("459580", DATE))
                .thenReturn(new TossCandleResponse(DATE, 10_000L, 10_500L, 10_600L, 9_900L));

        EtfPriceCollector.CollectionSummary summary = collector.collect(DATE);

        assertEquals(1, summary.successCount());
        assertEquals(1, summary.failureCount());
        assertEquals(List.of("999999"), summary.failedEtfCodes());
    }
}
