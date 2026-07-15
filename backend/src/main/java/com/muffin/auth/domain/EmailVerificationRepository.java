package com.muffin.auth.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** EmailVerification 애그리거트 리포지토리 */
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    // 해당 이메일의 현재 유효한 가장 최근 인증
    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);

    // 일일 재발송 횟수 제한(10회) 체크용. since에 당일 00:00을 넘겨 호출.
    long countByEmailAndCreatedAtAfter(String email, LocalDateTime since);

    // 정리 배치용. 인증 성공(verified=true) 레코드는 회원가입 서비스가 나중에 조회할 수 있어야 하므로 건드리지 않는다.
    @Modifying(clearAutomatically = true)
    @Query("delete from EmailVerification e where e.verified = false and e.expiresAt < :cutoff")
    int deleteExpiredUnverified(@Param("cutoff") LocalDateTime cutoff);
}
