package com.muffin.auth.application;

import com.muffin.auth.domain.EmailVerificationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만료된 미인증 이메일 인증 레코드를 정리하는 배치. 인증 성공(verified=true) 레코드는 회원가입 서비스가 나중에 조회할 수 있어야 하므로 건드리지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationCleanupService {

    private final EmailVerificationRepository emailVerificationRepository;

    @Transactional
    public void cleanupExpired() {
        long deleted = emailVerificationRepository.deleteByVerifiedFalseAndExpiresAtBefore(LocalDateTime.now());
        log.info("[email-verification-cleanup] deleted={}", deleted);
    }
}
