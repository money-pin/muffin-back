package com.muffin.auth.presentation.withdraw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.withdraw.WithdrawnDataCleanupResult;
import com.muffin.auth.application.withdraw.WithdrawnDataCleanupService;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchLogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawnDataCleanupSchedulerTest {

    @Mock
    private WithdrawnDataCleanupService withdrawnDataCleanupService;

    @Test
    @DisplayName("정리한 투자/퀴즈 유저 수를 배치 로그 한 줄에 각각 남긴다")
    void run_logsCleanedUserCounts() {
        WithdrawnDataCleanupScheduler scheduler =
                new WithdrawnDataCleanupScheduler(withdrawnDataCleanupService, new BatchJobRunner());
        when(withdrawnDataCleanupService.cleanupWithdrawnUserData()).thenReturn(new WithdrawnDataCleanupResult(3, 5));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.WITHDRAWN_DATA_CLEANUP)) {
            scheduler.run();

            assertThat(capture.line())
                    .contains("job=withdrawn_data_cleanup", "outcome=success", "investment_users=3", "quiz_users=5")
                    .doesNotContain("date=");
        }
    }
}
