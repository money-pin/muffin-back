package com.muffin.auth.presentation.signup;

import com.muffin.auth.application.signup.UnverifiedAccountCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 이메일 인증을 끝내지 않은 채 TTL이 지난 로컬 계정을 주기적으로(기본 매시 정각) 정리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnverifiedAccountCleanupScheduler {

    private final UnverifiedAccountCleanupService unverifiedAccountCleanupService;

    @Scheduled(
            cron = "${muffin.auth.signup.unverified-account-cleanup.cleanup-cron}",
            zone = "${muffin.auth.signup.unverified-account-cleanup.zone:Asia/Seoul}")
    public void run() {
        log.info("[unverified-account-cleanup] triggered by scheduler");
        unverifiedAccountCleanupService.cleanupUnverified();
    }
}
