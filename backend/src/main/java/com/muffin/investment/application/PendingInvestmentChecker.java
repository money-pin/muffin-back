package com.muffin.investment.application;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 조회 화면과 투자 명령이 공유하는 처리 대기 투자 판정 규칙. */
@Component
@RequiredArgsConstructor
public class PendingInvestmentChecker {

    private static final List<SettlementStatus> REPROCESSABLE =
            List.of(SettlementStatus.PENDING, SettlementStatus.FAILED);

    private final InvestmentRepository investmentRepository;

    public boolean hasPendingInvestment(Long userId, TradingCalendar calendar) {
        return investmentRepository
                .findByUserIdAndSettlementStatusInAndInvestDateLessThan(userId, REPROCESSABLE, calendar.date())
                .stream()
                .anyMatch(investment -> !isStaleConfirmed(investment, calendar.previousTradingDay()));
    }

    private boolean isStaleConfirmed(Investment investment, LocalDate previousTradingDay) {
        return investment.getStatus() == InvestmentStatus.CONFIRMED
                && investment.getInvestDate().isBefore(previousTradingDay);
    }
}
