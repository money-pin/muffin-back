package com.muffin.investment.presentation;

import com.muffin.investment.application.InvestmentFinalizationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 자정부터 10분 간격으로 전날 투자를 멱등 마감한다. 캘린더 장애나 일시적 DB 실패는 다음 실행에서 재시도한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.investment-finalization.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InvestmentFinalizationScheduler {

    private final InvestmentFinalizationService finalizationService;
    private final Clock clock;

    @Scheduled(
            cron = "${muffin.batch.investment-finalization.cron:0 0,10,20,30,40,50 0 * * *}",
            zone = "${muffin.batch.investment-finalization.zone:Asia/Seoul}")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate investDate = now.toLocalDate().minusDays(1);
        try {
            finalizationService.finalizeInvestments(investDate, now);
        } catch (RuntimeException exception) {
            log.error("[investment-finalization] aborted investDate={}", investDate, exception);
        }
    }
}
