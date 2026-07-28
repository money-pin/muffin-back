package com.muffin.ranking.presentation;

import com.muffin.ranking.application.WeeklyRankingBatchService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 지난주 랭킹이 아직 없을 때만 하루 세 차례 생성한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.weekly-ranking.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WeeklyRankingScheduler {

    private final WeeklyRankingBatchService weeklyRankingBatchService;
    private final Clock clock;

    @Scheduled(
            cron = "${muffin.batch.weekly-ranking.morning-cron:0 40,50 9 * * *}",
            zone = "${muffin.batch.weekly-ranking.zone:Asia/Seoul}")
    public void runMorning() {
        run();
    }

    @Scheduled(
            cron = "${muffin.batch.weekly-ranking.final-cron:0 0 10 * * *}",
            zone = "${muffin.batch.weekly-ranking.zone:Asia/Seoul}")
    public void runFinal() {
        run();
    }

    private void run() {
        LocalDate referenceDate = LocalDate.now(clock);
        try {
            weeklyRankingBatchService.createPreviousWeekRanking(referenceDate);
        } catch (RuntimeException exception) {
            log.error("[weekly-ranking] aborted referenceDate={}", referenceDate, exception);
        }
    }
}
