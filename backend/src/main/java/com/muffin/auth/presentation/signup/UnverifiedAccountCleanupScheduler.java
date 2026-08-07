package com.muffin.auth.presentation.signup;

import com.muffin.auth.application.signup.UnverifiedAccountCleanupService;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 이메일 인증을 끝내지 않은 채 TTL이 지난 로컬 계정을 주기적으로(기본 매시 정각) 정리한다. */
@Component
@RequiredArgsConstructor
public class UnverifiedAccountCleanupScheduler {

    private final UnverifiedAccountCleanupService unverifiedAccountCleanupService;
    private final BatchJobRunner batchJobRunner;

    @Scheduled(
            cron = "${muffin.auth.signup.unverified-account-cleanup.cleanup-cron}",
            zone = "${muffin.auth.signup.unverified-account-cleanup.zone:Asia/Seoul}")
    public void run() {
        batchJobRunner.run(BatchJob.UNVERIFIED_ACCOUNT_CLEANUP, BatchTrigger.SCHEDULER, () -> BatchJobReport.success()
                .with("deleted", unverifiedAccountCleanupService.cleanupUnverified()));
    }
}
