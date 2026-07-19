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

/** 자정부터 장 시작 전까지 전날과 과거 미완료 투자를 멱등 마감한다. 캘린더 장애나 일시적 DB 실패는 다음 실행에서 재시도한다. */
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
            finalizationService.finalizePendingDates(investDate, now);
        } catch (RuntimeException exception) {
            log.error("[investment-finalization] aborted investDate={}", investDate, exception);
        }
    }

    /** 00시대 실행을 모두 놓쳐도 다음 거래일 정산 전에 복구할 수 있도록 01:00~08:50에 계속 재시도한다. */
    @Scheduled(
            cron = "${muffin.batch.investment-finalization.recovery-cron:0 0/10 1-8 * * *}",
            zone = "${muffin.batch.investment-finalization.zone:Asia/Seoul}")
    public void recover() {
        run();
    }
}
