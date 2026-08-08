package com.muffin.auth.presentation.emailverification;

import com.muffin.auth.application.emailverification.EmailVerificationCleanupService;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료된 미인증 이메일 인증 레코드를 주기적으로(기본 매시 정각) 정리한다. */
@Component
@RequiredArgsConstructor
public class EmailVerificationCleanupScheduler {

    private final EmailVerificationCleanupService emailVerificationCleanupService;
    private final BatchJobRunner batchJobRunner;

    @Scheduled(
            cron = "${muffin.auth.email-verification.cleanup-cron}",
            zone = "${muffin.auth.email-verification.zone:Asia/Seoul}")
    public void run() {
        batchJobRunner.run(BatchJob.EMAIL_VERIFICATION_CLEANUP, BatchTrigger.SCHEDULER, () -> BatchJobReport.success()
                .with("deleted", emailVerificationCleanupService.cleanupExpired()));
    }
}
