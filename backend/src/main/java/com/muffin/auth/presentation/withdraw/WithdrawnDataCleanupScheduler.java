package com.muffin.auth.presentation.withdraw;

import com.muffin.auth.application.withdraw.WithdrawnDataCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 탈퇴 후 보관 기간(기본 6개월)이 지난 계정의 투자/퀴즈 기록을 주기적으로(기본 매일 새벽) 정리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnDataCleanupScheduler {

    private final WithdrawnDataCleanupService withdrawnDataCleanupService;

    @Scheduled(
            cron = "${muffin.auth.withdrawal.data-cleanup.cleanup-cron}",
            zone = "${muffin.auth.withdrawal.data-cleanup.zone:Asia/Seoul}")
    public void run() {
        log.info("[withdrawn-data-cleanup] triggered by scheduler");
        withdrawnDataCleanupService.cleanupWithdrawnUserData();
    }
}
