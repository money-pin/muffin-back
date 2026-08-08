package com.muffin.auth.application.emailverification;

import com.muffin.auth.domain.emailverification.EmailVerificationRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만료된 미인증 이메일 인증 레코드를 정리하는 배치. 인증 성공(verified=true) 레코드는 회원가입 서비스가 나중에 조회할 수 있어야 하므로 건드리지 않는다. */
@Service
@RequiredArgsConstructor
public class EmailVerificationCleanupService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final EmailVerificationRepository emailVerificationRepository;

    /**
     * @return 삭제한 레코드 수. 호출자가 배치 실행 로그에 남긴다.
     */
    @Transactional
    public int cleanupExpired() {
        return emailVerificationRepository.deleteExpiredUnverified(LocalDateTime.now(KST));
    }
}
