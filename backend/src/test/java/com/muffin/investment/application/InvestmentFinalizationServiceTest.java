package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.user.domain.enums.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/** 자정 마감 오케스트레이터는 거래일 여부만 확인하고 사용자별 동결을 위임한다(가격 스냅샷은 정산 phase 1의 책임). */
@ExtendWith(MockitoExtension.class)
class InvestmentFinalizationServiceTest {

    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDateTime FINALIZED_AT = LocalDateTime.of(2026, 7, 14, 0, 0);

    @Mock
    private TradingCalendarService tradingCalendarService;

    @Mock
    private UserAssetRepository userAssetRepository;

    @Mock
    private InvestmentFinalizationProcessor processor;

    private InvestmentFinalizationService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentFinalizationService(tradingCalendarService, userAssetRepository, processor);
    }

    @Test
    @DisplayName("전날이 휴장일이면 사용자를 조회하지 않고 마감을 건너뛴다")
    void finalizeInvestments_skipsMarketClosed() {
        when(tradingCalendarService.getCalendar(INVEST_DATE))
                .thenReturn(new TradingCalendar(INVEST_DATE, false, INVEST_DATE.minusDays(3), INVEST_DATE.plusDays(1)));

        InvestmentFinalizationResult result = service.finalizeInvestments(INVEST_DATE, FINALIZED_AT);

        assertEquals(false, result.tradingDay());
        verify(userAssetRepository, never())
                .findByCreatedAtBeforeAndUserStatus(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(UserStatus.ACTIVE),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("거래일이면 마감 전에 생성된 사용자를 동결 처리한다")
    void finalizeInvestments_processesEligibleAssets() {
        when(tradingCalendarService.getCalendar(INVEST_DATE))
                .thenReturn(new TradingCalendar(INVEST_DATE, true, INVEST_DATE.minusDays(3), INVEST_DATE.plusDays(1)));
        UserAsset asset = UserAsset.create(1L, 1_000_000L);
        ReflectionTestUtils.setField(asset, "id", 10L);
        var pageRequest = PageRequest.of(0, 500, Sort.by("id").ascending());
        when(userAssetRepository.findByCreatedAtBeforeAndUserStatus(
                        INVEST_DATE.plusDays(1).atStartOfDay(), UserStatus.ACTIVE, pageRequest))
                .thenReturn(new SliceImpl<>(List.of(asset), pageRequest, false));

        InvestmentFinalizationResult result = service.finalizeInvestments(INVEST_DATE, FINALIZED_AT);

        assertEquals(1, result.targetCount());
        assertEquals(1, result.successCount());
        verify(processor).finalizeUser(10L, INVEST_DATE, FINALIZED_AT);
    }

    @Test
    @DisplayName("여러 슬라이스에 걸친 사용자를 모두 마감 처리한다")
    void finalizeInvestments_processesAllSlices() {
        when(tradingCalendarService.getCalendar(INVEST_DATE))
                .thenReturn(new TradingCalendar(INVEST_DATE, true, INVEST_DATE.minusDays(3), INVEST_DATE.plusDays(1)));
        UserAsset first = UserAsset.create(1L, 1_000_000L);
        UserAsset second = UserAsset.create(2L, 1_000_000L);
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(second, "id", 20L);
        var firstPage = PageRequest.of(0, 500, Sort.by("id").ascending());
        var secondPage = firstPage.next();
        when(userAssetRepository.findByCreatedAtBeforeAndUserStatus(
                        INVEST_DATE.plusDays(1).atStartOfDay(), UserStatus.ACTIVE, firstPage))
                .thenReturn(new SliceImpl<>(List.of(first), firstPage, true));
        when(userAssetRepository.findByCreatedAtBeforeAndUserStatus(
                        INVEST_DATE.plusDays(1).atStartOfDay(), UserStatus.ACTIVE, secondPage))
                .thenReturn(new SliceImpl<>(List.of(second), secondPage, false));

        InvestmentFinalizationResult result = service.finalizeInvestments(INVEST_DATE, FINALIZED_AT);

        assertEquals(2, result.targetCount());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        verify(processor).finalizeUser(10L, INVEST_DATE, FINALIZED_AT);
        verify(processor).finalizeUser(20L, INVEST_DATE, FINALIZED_AT);
    }
}
