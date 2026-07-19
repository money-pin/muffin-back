package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.investment.exception.InvestmentErrorCode;
import com.muffin.investment.presentation.dto.InvestmentRequest;
import com.muffin.investment.presentation.dto.InvestmentSectorRequest;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentCommandServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);
    private static final LocalDate PREVIOUS_TRADING_DAY = LocalDate.of(2026, 7, 10);
    private static final LocalDate NEXT_TRADING_DAY = LocalDate.of(2026, 7, 14);
    private static final List<SettlementStatus> REPROCESSABLE =
            List.of(SettlementStatus.PENDING, SettlementStatus.FAILED);

    @Mock
    private UserAssetRepository userAssetRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private SectorRepository sectorRepository;

    @Mock
    private TradingCalendarService tradingCalendarService;

    private InvestmentCommandService service;
    private UserAsset asset;
    private Sector semiconductor;

    @BeforeEach
    void setUp() {
        service = serviceAt("2026-07-13T10:00:00+09:00");
        asset = UserAsset.create(USER_ID, 1_000_000L);
        ReflectionTestUtils.setField(asset, "id", 10L);
        semiconductor = sector(100L, "SEMICONDUCTOR", "반도체");
    }

    @Test
    @DisplayName("최초 투자 확정은 중복 섹터 수량을 합산해 생성한다")
    void confirm_createsNormalizedInvestment() {
        mockAssetAndAvailableWindow();
        when(investmentRepository.findWithSectorsByUserIdAndInvestDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        when(sectorRepository.findBySectorCode("SEMICONDUCTOR")).thenReturn(Optional.of(semiconductor));

        InvestmentCommandResult result = service.confirm(
                USER_ID,
                request(
                        new InvestmentSectorRequest("SEMICONDUCTOR", 2),
                        new InvestmentSectorRequest("SEMICONDUCTOR", 3)));

        assertTrue(result.created());
        assertEquals(500_000L, result.response().totalAmount());
        assertEquals(500_000L, result.response().remainingAmount());
        assertEquals(5, result.response().sectors().getFirst().quantity());

        ArgumentCaptor<Investment> captor = ArgumentCaptor.forClass(Investment.class);
        verify(investmentRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getSectors().size());
        assertEquals(500_000L, captor.getValue().getTotalAmount());
    }

    @Test
    @DisplayName("같은 정규화 POST 재요청은 기존 결과를 200 대상으로 반환한다")
    void confirm_sameRequestReturnsExistingResult() {
        when(userAssetRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(asset));
        when(sectorRepository.findBySectorCode("SEMICONDUCTOR")).thenReturn(Optional.of(semiconductor));
        Investment existing = Investment.confirm(USER_ID, asset.getId(), TODAY);
        existing.addSector(semiconductor.getId(), 5, 500_000L, null);
        when(investmentRepository.findWithSectorsByUserIdAndInvestDate(USER_ID, TODAY))
                .thenReturn(Optional.of(existing));

        InvestmentCommandResult result = service.confirm(
                USER_ID,
                request(
                        new InvestmentSectorRequest("SEMICONDUCTOR", 2),
                        new InvestmentSectorRequest("SEMICONDUCTOR", 3)));

        assertFalse(result.created());
        assertEquals(500_000L, result.response().totalAmount());
        verify(tradingCalendarService, never()).getCalendar(TODAY);
        verify(investmentRepository, never()).save(existing);
    }

    @Test
    @DisplayName("이미 다른 구성으로 확정했으면 409 도메인 오류가 발생한다")
    void confirm_differentRequestThrowsConflict() {
        when(userAssetRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(asset));
        when(sectorRepository.findBySectorCode("SEMICONDUCTOR")).thenReturn(Optional.of(semiconductor));
        Investment existing = Investment.confirm(USER_ID, asset.getId(), TODAY);
        existing.addSector(semiconductor.getId(), 1, 100_000L, null);
        when(investmentRepository.findWithSectorsByUserIdAndInvestDate(USER_ID, TODAY))
                .thenReturn(Optional.of(existing));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.confirm(USER_ID, request(new InvestmentSectorRequest("SEMICONDUCTOR", 2))));

        assertEquals(InvestmentErrorCode.INVESTMENT_ALREADY_CONFIRMED, exception.getErrorCode());
    }

    @Test
    @DisplayName("총 투자금이 자산을 초과하면 확정을 거부한다")
    void confirm_rejectsBudgetExceeded() {
        mockAssetAndAvailableWindow();
        when(investmentRepository.findWithSectorsByUserIdAndInvestDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        when(sectorRepository.findBySectorCode("SEMICONDUCTOR")).thenReturn(Optional.of(semiconductor));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.confirm(USER_ID, request(new InvestmentSectorRequest("SEMICONDUCTOR", 11))));

        assertEquals(InvestmentErrorCode.BUDGET_EXCEEDED, exception.getErrorCode());
    }

    @Test
    @DisplayName("PATCH는 기존 섹터 구성을 정규화된 요청으로 전체 교체한다")
    void updateToday_replacesAllSectors() {
        mockAssetAndAvailableWindow();
        Investment existing = Investment.confirm(USER_ID, asset.getId(), TODAY);
        existing.addSector(999L, 1, 100_000L, null);
        when(investmentRepository.findWithSectorsForUpdate(USER_ID, TODAY)).thenReturn(Optional.of(existing));
        when(sectorRepository.findBySectorCode("SEMICONDUCTOR")).thenReturn(Optional.of(semiconductor));

        TodayInvestmentResponse response =
                service.updateToday(USER_ID, request(new InvestmentSectorRequest("SEMICONDUCTOR", 4)));

        assertEquals(400_000L, response.totalAmount());
        assertEquals(1, existing.getSectors().size());
        assertEquals(semiconductor.getId(), existing.getSectors().getFirst().getSectorId());
        assertEquals(4, existing.getSectors().getFirst().getQuantity());
    }

    @Test
    @DisplayName("거래일 10시 전에는 투자 확정을 거부한다")
    void confirm_rejectsBeforeTen() {
        service = serviceAt("2026-07-13T09:59:59+09:00");
        when(userAssetRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(asset));
        when(investmentRepository.findWithSectorsByUserIdAndInvestDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        when(sectorRepository.findBySectorCode("SEMICONDUCTOR")).thenReturn(Optional.of(semiconductor));
        mockTradingDay();

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> service.confirm(USER_ID, request(new InvestmentSectorRequest("SEMICONDUCTOR", 1))));

        assertEquals(InvestmentErrorCode.INVESTMENT_WINDOW_CLOSED, exception.getErrorCode());
    }

    private void mockAssetAndAvailableWindow() {
        when(userAssetRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(asset));
        mockTradingDay();
        when(investmentRepository.findByUserIdAndSettlementStatusInAndInvestDateLessThan(USER_ID, REPROCESSABLE, TODAY))
                .thenReturn(List.of());
    }

    private void mockTradingDay() {
        when(tradingCalendarService.getCalendar(TODAY))
                .thenReturn(new TradingCalendar(TODAY, true, PREVIOUS_TRADING_DAY, NEXT_TRADING_DAY));
    }

    private InvestmentCommandService serviceAt(String timestamp) {
        Clock clock = Clock.fixed(OffsetDateTime.parse(timestamp).toInstant(), KST);
        return new InvestmentCommandService(
                userAssetRepository, investmentRepository, sectorRepository, tradingCalendarService, clock);
    }

    private static InvestmentRequest request(InvestmentSectorRequest... sectors) {
        return new InvestmentRequest(List.of(sectors));
    }

    private static Sector sector(Long id, String code, String name) {
        Sector sector = Sector.create(1L, 1000L + id, name, "", code, 1);
        ReflectionTestUtils.setField(sector, "id", id);
        return sector;
    }
}
