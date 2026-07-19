package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingInvestmentCheckerTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 13);
    private static final LocalDate PREVIOUS_TRADING_DAY = LocalDate.of(2026, 7, 10);
    private static final List<SettlementStatus> REPROCESSABLE =
            List.of(SettlementStatus.PENDING, SettlementStatus.FAILED);
    private static final TradingCalendar CALENDAR =
            new TradingCalendar(TODAY, true, PREVIOUS_TRADING_DAY, TODAY.plusDays(1));

    @Mock
    private InvestmentRepository investmentRepository;

    private PendingInvestmentChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PendingInvestmentChecker(investmentRepository);
    }

    @Test
    @DisplayName("직전 거래일의 확정 투자는 처리 대기로 판정한다")
    void hasPendingInvestment_returnsTrueForPreviousTradingDay() {
        Investment pending = Investment.confirm(USER_ID, 10L, PREVIOUS_TRADING_DAY);
        mockInvestments(List.of(pending));

        assertTrue(checker.hasPendingInvestment(USER_ID, CALENDAR));
    }

    @Test
    @DisplayName("직전 거래일보다 오래된 확정 투자는 취소 대상이므로 처리 대기에서 제외한다")
    void hasPendingInvestment_excludesStaleConfirmedInvestment() {
        Investment stale = Investment.confirm(USER_ID, 10L, PREVIOUS_TRADING_DAY.minusDays(1));
        mockInvestments(List.of(stale));

        assertFalse(checker.hasPendingInvestment(USER_ID, CALENDAR));
    }

    @Test
    @DisplayName("오래된 NO_INVEST는 정산 종료가 필요하므로 처리 대기로 판정한다")
    void hasPendingInvestment_keepsStaleNoInvestPending() {
        Investment noInvest = Investment.noInvest(USER_ID, 10L, PREVIOUS_TRADING_DAY.minusDays(1));
        mockInvestments(List.of(noInvest));

        assertTrue(checker.hasPendingInvestment(USER_ID, CALENDAR));
    }

    private void mockInvestments(List<Investment> investments) {
        when(investmentRepository.findByUserIdAndSettlementStatusInAndInvestDateLessThan(USER_ID, REPROCESSABLE, TODAY))
                .thenReturn(investments);
    }
}
