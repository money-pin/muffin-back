package com.muffin.ranking.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.ranking.application.WeeklyRankingBatchResult;
import com.muffin.ranking.application.WeeklyRankingBatchService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 지난주 랭킹이 아직 없을 때만 하루 세 차례 생성한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.weekly-ranking.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WeeklyRankingScheduler {

    private final WeeklyRankingBatchService weeklyRankingBatchService;
    private final BatchJobRunner batchJobRunner;
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
        batchJobRunner.run(BatchJob.WEEKLY_RANKING, BatchTrigger.SCHEDULER, referenceDate, () -> {
            WeeklyRankingBatchResult result = weeklyRankingBatchService.createPreviousWeekRanking(referenceDate);
            return report(result).with("week_start", result.weekStartDate());
        });
    }

    private BatchJobReport report(WeeklyRankingBatchResult result) {
        return switch (result.outcome()) {
            case CREATED -> BatchJobReport.success().with("participants", result.participantCount());
            case ALREADY_CREATED -> BatchJobReport.skipped("already_created");
            case SETTLEMENT_PENDING -> BatchJobReport.skipped("settlement_pending");
            case NO_PARTICIPANTS -> BatchJobReport.skipped("no_participants");
        };
    }
}
