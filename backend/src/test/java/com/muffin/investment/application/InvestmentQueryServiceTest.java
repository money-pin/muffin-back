package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.investment.domain.exception.InvestmentDataIntegrityException;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.investment.presentation.dto.AssetChangeDirection;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentStatus;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvestmentQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 13);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 7, 14);
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

    private InvestmentQueryService service;

    @BeforeEach
    void setUp() {
        service = serviceAt("2026-07-13T10:00:00+09:00");
    }

    @Test
    @DisplayName("UserAsset이 없으면 외부 캘린더 조회 없이 초기 자산 기본값을 반환한다")
    void getAsset_returnsDefaultForUninitializedUser() {
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        InvestmentAssetResponse response = service.getAsset(USER_ID);

        assertEquals(1_000_000L, response.totalAsset());
        assertEquals(0L, response.dailyChangeAmount());
        assertEquals(AssetChangeDirection.NONE, response.changeDirection());
        assertFalse(response.settlementPending());
        verify(tradingCalendarService, never()).getCalendar(MONDAY);
    }

    @Test
    @DisplayName("정산 대기 시간에는 처리 대기 투자가 있으면 총자산 응답에 settlementPending을 표시한다")
    void getAsset_marksSettlementPendingBeforeSettlementWindow() {
        service = serviceAt("2026-07-13T08:00:00+09:00");
        UserAsset asset = UserAsset.create(USER_ID, 1_000_000L);
        asset.applySettlement(45_000L, new BigDecimal("4.5000"), LocalDateTime.of(2026, 7, 10, 9, 5));
        Investment pending = Investment.confirm(USER_ID, PREVIOUS_TRADING_DAY);
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(asset));
        mockTradingMonday();
        when(investmentRepository.findByUserIdAndSettlementStatusInAndInvestDateLessThan(
                        USER_ID, REPROCESSABLE, MONDAY))
                .thenReturn(List.of(pending));

        InvestmentAssetResponse response = service.getAsset(USER_ID);

        assertEquals(1_045_000L, response.totalAsset());
        assertEquals(45_000L, response.dailyChangeAmount());
        assertEquals(AssetChangeDirection.UP, response.changeDirection());
        assertTrue(response.settlementPending());
        verify(tradingCalendarService).getCalendar(MONDAY);
    }

    @Test
    @DisplayName("주말에는 다음 거래일 10시와 CLOSED_WEEKEND를 반환한다")
    void getToday_returnsClosedWeekend() {
        LocalDate saturday = LocalDate.of(2026, 7, 11);
        service = serviceAt("2026-07-11T12:00:00+09:00");
        when(tradingCalendarService.getCalendar(saturday))
                .thenReturn(new TradingCalendar(saturday, false, PREVIOUS_TRADING_DAY, MONDAY));

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.CLOSED_WEEKEND, response.status());
        assertEquals(
                "2026-07-13T10:00+09:00", response.nextInvestmentAvailableAt().toString());
    }

    @Test
    @DisplayName("평일 휴장일에는 CLOSED_HOLIDAY를 반환한다")
    void getToday_returnsClosedHoliday() {
        when(tradingCalendarService.getCalendar(MONDAY))
                .thenReturn(new TradingCalendar(MONDAY, false, PREVIOUS_TRADING_DAY, NEXT_TRADING_DAY));

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.CLOSED_HOLIDAY, response.status());
        assertEquals(
                "2026-07-14T10:00+09:00", response.nextInvestmentAvailableAt().toString());
    }

    @Test
    @DisplayName("거래일 09시 전에는 당일 10시까지 UNAVAILABLE이다")
    void getToday_returnsUnavailableBeforeNine() {
        service = serviceAt("2026-07-13T08:59:59+09:00");
        mockTradingMonday();
        mockPreviousFridayInvestment();

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.UNAVAILABLE, response.status());
        assertEquals(
                "2026-07-13T10:00+09:00", response.nextInvestmentAvailableAt().toString());
        assertNotNull(response.previousInvestment());
        assertEquals(PREVIOUS_TRADING_DAY, response.previousInvestment().investDate());
        assertEquals(200_000L, response.previousInvestment().totalAmount());
        assertEquals("GOLD", response.previousInvestment().sectors().getFirst().sectorCode());
    }

    @Test
    @DisplayName("공휴일 다음 거래일 오전에는 캘린더가 제공한 직전 거래일 투자 내역을 반환한다")
    void getToday_returnsPreviousTradingDayInvestmentAfterHoliday() {
        service = serviceAt("2026-07-14T08:00:00+09:00");
        when(tradingCalendarService.getCalendar(TUESDAY))
                .thenReturn(new TradingCalendar(TUESDAY, true, PREVIOUS_TRADING_DAY, LocalDate.of(2026, 7, 15)));
        mockPreviousFridayInvestment();

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.UNAVAILABLE, response.status());
        assertNotNull(response.previousInvestment());
        assertEquals(PREVIOUS_TRADING_DAY, response.previousInvestment().investDate());
    }

    @Test
    @DisplayName("직전 투자 내역의 섹터 기준정보가 없으면 서버 데이터 정합성 오류가 발생한다")
    void getToday_throwsDataIntegrityExceptionWhenSectorReferenceIsMissing() {
        service = serviceAt("2026-07-13T08:00:00+09:00");
        mockTradingMonday();
        Investment investment = Investment.confirm(USER_ID, PREVIOUS_TRADING_DAY);
        investment.addSector(1L, 2, 200_000L, null);
        when(investmentRepository.findWithSectorsByUserIdAndInvestDateAndStatus(
                        USER_ID, PREVIOUS_TRADING_DAY, InvestmentStatus.CONFIRMED))
                .thenReturn(Optional.of(investment));
        when(sectorRepository.findAllById(List.of(1L))).thenReturn(List.of());

        InvestmentDataIntegrityException exception =
                assertThrows(InvestmentDataIntegrityException.class, () -> service.getToday(USER_ID));

        assertTrue(exception.getMessage().contains("sectorId=1"));
    }

    @Test
    @DisplayName("거래일 09시 이상 10시 전에는 SETTLING이다")
    void getToday_returnsSettlingBetweenNineAndTen() {
        service = serviceAt("2026-07-13T09:00:00+09:00");
        mockTradingMonday();
        mockPreviousFridayInvestment();

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.SETTLING, response.status());
        assertNotNull(response.previousInvestment());
        assertEquals(PREVIOUS_TRADING_DAY, response.previousInvestment().investDate());
    }

    @Test
    @DisplayName("거래일 09:59:59까지 SETTLING 상태다")
    void getToday_returnsSettlingUntilBeforeTen() {
        service = serviceAt("2026-07-13T09:59:59+09:00");
        mockTradingMonday();

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.SETTLING, response.status());
        assertNull(response.previousInvestment());
    }

    @Test
    @DisplayName("10시 이후 직전 거래일의 미정산 투자가 있으면 SETTLEMENT_DELAYED이다")
    void getToday_returnsSettlementDelayedForPendingInvestment() {
        mockTradingMonday();
        Investment pending = Investment.confirm(USER_ID, PREVIOUS_TRADING_DAY);
        when(investmentRepository.findByUserIdAndSettlementStatusInAndInvestDateLessThan(
                        USER_ID, REPROCESSABLE, MONDAY))
                .thenReturn(List.of(pending));

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.SETTLEMENT_DELAYED, response.status());
    }

    @Test
    @DisplayName("직전 거래일보다 오래된 확정 투자는 취소 대상이므로 정산 지연을 만들지 않는다")
    void getToday_excludesStaleConfirmedInvestmentFromPending() {
        mockTradingMonday();
        Investment stale = Investment.confirm(USER_ID, PREVIOUS_TRADING_DAY.minusDays(1));
        when(investmentRepository.findByUserIdAndSettlementStatusInAndInvestDateLessThan(
                        USER_ID, REPROCESSABLE, MONDAY))
                .thenReturn(List.of(stale));
        when(investmentRepository.findWithSectorsByUserIdAndInvestDateAndStatus(
                        USER_ID, MONDAY, InvestmentStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.AVAILABLE, response.status());
    }

    @Test
    @DisplayName("오늘 확정 투자가 있으면 남은 금액과 섹터별 비중을 반환한다")
    void getToday_returnsConfirmedInvestmentDetails() {
        mockTradingMonday();
        when(investmentRepository.findByUserIdAndSettlementStatusInAndInvestDateLessThan(
                        USER_ID, REPROCESSABLE, MONDAY))
                .thenReturn(List.of());
        UserAsset asset = UserAsset.create(USER_ID, 1_000_000L);
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(asset));

        Sector gold = sector(1L, "GOLD", "금", 2);
        Sector semiconductor = sector(2L, "SEMICONDUCTOR", "반도체", 1);
        Investment investment = Investment.confirm(USER_ID, MONDAY);
        investment.addSector(gold.getId(), 1, 100_000L, null);
        investment.addSector(semiconductor.getId(), 6, 600_000L, null);
        when(investmentRepository.findWithSectorsByUserIdAndInvestDateAndStatus(
                        USER_ID, MONDAY, InvestmentStatus.CONFIRMED))
                .thenReturn(Optional.of(investment));
        when(sectorRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(gold, semiconductor));

        TodayInvestmentResponse response = service.getToday(USER_ID);

        assertEquals(TodayInvestmentStatus.CONFIRMED_EDITABLE, response.status());
        assertEquals(300_000L, response.remainingAmount());
        assertEquals(700_000L, response.totalAmount());
        assertEquals("2026-07-14T00:00+09:00", response.confirmDeadline().toString());
        assertEquals(
                List.of("GOLD", "SEMICONDUCTOR"),
                response.sectors().stream()
                        .map(sectorResponse -> sectorResponse.sectorCode())
                        .toList());
        assertEquals(new BigDecimal("85.71"), response.sectors().get(1).ratio());
    }

    private InvestmentQueryService serviceAt(String instant) {
        Clock clock = Clock.fixed(OffsetDateTime.parse(instant).toInstant(), KST);
        return new InvestmentQueryService(
                userAssetRepository,
                investmentRepository,
                sectorRepository,
                tradingCalendarService,
                new PendingInvestmentChecker(investmentRepository),
                clock);
    }

    private void mockTradingMonday() {
        when(tradingCalendarService.getCalendar(MONDAY))
                .thenReturn(new TradingCalendar(MONDAY, true, PREVIOUS_TRADING_DAY, NEXT_TRADING_DAY));
    }

    private void mockPreviousFridayInvestment() {
        Sector gold = sector(1L, "GOLD", "금", 1);
        Investment investment = Investment.confirm(USER_ID, PREVIOUS_TRADING_DAY);
        investment.addSector(gold.getId(), 2, 200_000L, null);
        when(investmentRepository.findWithSectorsByUserIdAndInvestDateAndStatus(
                        USER_ID, PREVIOUS_TRADING_DAY, InvestmentStatus.CONFIRMED))
                .thenReturn(Optional.of(investment));
        when(sectorRepository.findAllById(List.of(gold.getId()))).thenReturn(List.of(gold));
    }

    private static Sector sector(Long id, String code, String name, int order) {
        Sector sector = Sector.create(1L, id + 100L, name, "", code, order);
        ReflectionTestUtils.setField(sector, "id", id);
        return sector;
    }
}
