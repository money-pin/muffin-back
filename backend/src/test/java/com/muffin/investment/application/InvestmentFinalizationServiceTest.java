package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentFinalizationServiceTest {

    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDateTime FINALIZED_AT = LocalDateTime.of(2026, 7, 14, 0, 0);

    @Mock
    private TradingCalendarService tradingCalendarService;

    @Mock
    private UserAssetRepository userAssetRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private SectorRepository sectorRepository;

    @Mock
    private EtfPriceRepository etfPriceRepository;

    @Mock
    private InvestmentFinalizationProcessor processor;

    private InvestmentFinalizationService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentFinalizationService(
                tradingCalendarService,
                userAssetRepository,
                investmentRepository,
                sectorRepository,
                etfPriceRepository,
                processor);
    }

    @Test
    @DisplayName("전날이 휴장일이면 사용자와 가격을 조회하지 않고 마감을 건너뛴다")
    void finalizeInvestments_skipsMarketClosed() {
        when(tradingCalendarService.getCalendar(INVEST_DATE))
                .thenReturn(new TradingCalendar(INVEST_DATE, false, INVEST_DATE.minusDays(3), INVEST_DATE.plusDays(1)));

        InvestmentFinalizationResult result = service.finalizeInvestments(INVEST_DATE, FINALIZED_AT);

        assertEquals(false, result.tradingDay());
        verify(userAssetRepository, never()).findByCreatedAtBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("거래일이면 마감 전에 생성된 사용자에게 종가 스냅샷을 전달한다")
    void finalizeInvestments_processesEligibleAssets() {
        when(tradingCalendarService.getCalendar(INVEST_DATE))
                .thenReturn(new TradingCalendar(INVEST_DATE, true, INVEST_DATE.minusDays(3), INVEST_DATE.plusDays(1)));
        UserAsset asset = UserAsset.create(1L, 1_000_000L);
        ReflectionTestUtils.setField(asset, "id", 10L);
        when(userAssetRepository.findByCreatedAtBefore(INVEST_DATE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(asset));
        Sector sector = Sector.create(1L, 100L, "반도체", "", "SEMICONDUCTOR", 1);
        ReflectionTestUtils.setField(sector, "id", 200L);
        when(sectorRepository.findAll()).thenReturn(List.of(sector));
        when(etfPriceRepository.findByPriceDate(INVEST_DATE))
                .thenReturn(List.of(EtfPrice.create(100L, INVEST_DATE, null, 12_345L)));

        InvestmentFinalizationResult result = service.finalizeInvestments(INVEST_DATE, FINALIZED_AT);

        assertEquals(1, result.targetCount());
        assertEquals(1, result.successCount());
        verify(processor)
                .finalizeUser(10L, INVEST_DATE, FINALIZED_AT, java.util.Map.of(200L, BigDecimal.valueOf(12_345)));
    }

    @Test
    @DisplayName("연속으로 누락된 거래일은 오래된 날짜부터 복구한다")
    void finalizePendingDates_recoversOldestFirst() {
        LocalDate latest = LocalDate.of(2026, 7, 14);
        LocalDate older = LocalDate.of(2026, 7, 13);
        LocalDate completed = LocalDate.of(2026, 7, 10);
        mockTradingDay(latest, older);
        mockTradingDay(older, completed);
        mockTradingDay(completed, completed.minusDays(1));
        UserAsset asset = asset(10L);
        when(userAssetRepository.countByCreatedAtBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);
        when(userAssetRepository.findByCreatedAtBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(asset));
        when(investmentRepository.countCompletedFinalizationsByInvestDate(latest))
                .thenReturn(0L, 1L);
        when(investmentRepository.countCompletedFinalizationsByInvestDate(older))
                .thenReturn(0L, 1L);
        when(investmentRepository.countCompletedFinalizationsByInvestDate(completed))
                .thenReturn(1L);
        when(sectorRepository.findAll()).thenReturn(List.of());
        when(etfPriceRepository.findByPriceDate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        List<InvestmentFinalizationResult> results = service.finalizePendingDates(latest, FINALIZED_AT);

        assertEquals(
                List.of(older, latest),
                results.stream().map(InvestmentFinalizationResult::investDate).toList());
        InOrder inOrder = org.mockito.Mockito.inOrder(processor);
        inOrder.verify(processor).finalizeUser(10L, older, FINALIZED_AT, java.util.Map.of());
        inOrder.verify(processor).finalizeUser(10L, latest, FINALIZED_AT, java.util.Map.of());
    }

    @Test
    @DisplayName("오래된 날짜가 여전히 불완전하면 최신 날짜 처리를 보류한다")
    void finalizePendingDates_stopsWhenOlderDateRemainsIncomplete() {
        LocalDate latest = LocalDate.of(2026, 7, 14);
        LocalDate older = LocalDate.of(2026, 7, 13);
        LocalDate completed = LocalDate.of(2026, 7, 10);
        mockTradingDay(latest, older);
        mockTradingDay(older, completed);
        mockTradingDay(completed, completed.minusDays(1));
        UserAsset asset = asset(10L);
        when(userAssetRepository.countByCreatedAtBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);
        when(userAssetRepository.findByCreatedAtBefore(older.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(asset));
        when(investmentRepository.countCompletedFinalizationsByInvestDate(latest))
                .thenReturn(0L);
        when(investmentRepository.countCompletedFinalizationsByInvestDate(older))
                .thenReturn(0L, 0L);
        when(investmentRepository.countCompletedFinalizationsByInvestDate(completed))
                .thenReturn(1L);
        when(sectorRepository.findAll()).thenReturn(List.of());
        when(etfPriceRepository.findByPriceDate(older)).thenReturn(List.of());

        List<InvestmentFinalizationResult> results = service.finalizePendingDates(latest, FINALIZED_AT);

        assertEquals(
                List.of(older),
                results.stream().map(InvestmentFinalizationResult::investDate).toList());
        verify(processor).finalizeUser(10L, older, FINALIZED_AT, java.util.Map.of());
        verify(processor, times(1))
                .finalizeUser(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyMap());
    }

    private void mockTradingDay(LocalDate date, LocalDate previousTradingDay) {
        when(tradingCalendarService.getCalendar(date))
                .thenReturn(new TradingCalendar(date, true, previousTradingDay, date.plusDays(1)));
    }

    private UserAsset asset(Long id) {
        UserAsset asset = UserAsset.create(1L, 1_000_000L);
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
