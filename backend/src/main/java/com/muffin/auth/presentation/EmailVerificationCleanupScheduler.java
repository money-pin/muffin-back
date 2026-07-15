package com.muffin.auth.presentation;

import com.muffin.auth.application.EmailVerificationCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료된 미인증 이메일 인증 레코드를 주기적으로(기본 매시 정각) 정리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationCleanupScheduler {

    private final EmailVerificationCleanupService emailVerificationCleanupService;

    @Scheduled(
            cron = "${muffin.auth.email-verification.cleanup-cron}",
            zone = "${muffin.auth.email-verification.zone:Asia/Seoul}")
    public void run() {
        log.info("[email-verification-cleanup] triggered by scheduler");
        emailVerificationCleanupService.cleanupExpired();
    }
}
