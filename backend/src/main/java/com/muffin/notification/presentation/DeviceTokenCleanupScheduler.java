package com.muffin.notification.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.notification.application.devicetoken.DeviceTokenCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceTokenCleanupScheduler {
    private final DeviceTokenCleanupService cleanupService;
    private final BatchJobRunner batchJobRunner;

    @Scheduled(
            cron = "${muffin.batch.device-token-cleanup.cron:0 0 3 * * *}",
            zone = "${muffin.batch.device-token-cleanup.zone:Asia/Seoul}")
    public void cleanup() {
        batchJobRunner.run(BatchJob.DEVICE_TOKEN_CLEANUP, BatchTrigger.SCHEDULER, () -> BatchJobReport.success()
                .with("deleted", cleanupService.deleteExpired()));
    }
}
