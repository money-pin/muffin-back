package com.muffin.auth.presentation.withdraw;

import com.muffin.auth.application.withdraw.WithdrawnDataCleanupResult;
import com.muffin.auth.application.withdraw.WithdrawnDataCleanupService;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 탈퇴 후 보관 기간(기본 6개월)이 지난 계정의 투자/퀴즈 기록을 주기적으로(기본 매일 새벽) 정리한다. */
@Component
@RequiredArgsConstructor
public class WithdrawnDataCleanupScheduler {

    private final WithdrawnDataCleanupService withdrawnDataCleanupService;
    private final BatchJobRunner batchJobRunner;

    @Scheduled(
            cron = "${muffin.auth.withdrawal.data-cleanup.cleanup-cron}",
            zone = "${muffin.auth.withdrawal.data-cleanup.zone:Asia/Seoul}")
    public void run() {
        batchJobRunner.run(BatchJob.WITHDRAWN_DATA_CLEANUP, BatchTrigger.SCHEDULER, () -> {
            WithdrawnDataCleanupResult result = withdrawnDataCleanupService.cleanupWithdrawnUserData();
            return BatchJobReport.success()
                    .with("investment_users", result.investmentUserCount())
                    .with("quiz_users", result.quizUserCount());
        });
    }
}
