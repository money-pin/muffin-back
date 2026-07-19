package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private SectorRepository sectorRepository;

    @Mock
    private EtfPriceRepository etfPriceRepository;

    @Mock
    private InvestmentFinalizationProcessor processor;

    private InvestmentFinalizationService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentFinalizationService(
                tradingCalendarService, userAssetRepository, sectorRepository, etfPriceRepository, processor);
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
}
