package com.muffin.auth.domain.auth;

import com.muffin.auth.domain.enums.AuthProvider;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Auth 애그리거트 리포지토리 */
public interface AuthRepository extends JpaRepository<Auth, Long> {

    boolean existsByEmail(String email);

    Optional<Auth> findByUserId(Long userId);

    Optional<Auth> findByProviderAndEmail(AuthProvider provider, String email);

    // 로그인 전용. 동시 요청 간 failedLoginAttempts lost update(계정 잠금 우회)를 막기 위해 비관적 락으로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auth a where a.provider = :provider and a.email = :email")
    Optional<Auth> findByProviderAndEmailForUpdate(
            @Param("provider") AuthProvider provider, @Param("email") String email);

    Optional<Auth> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    // 미인증 계정 정리 배치용
    List<Auth> findAllByProviderAndEmailVerifiedFalseAndCreatedAtBefore(AuthProvider provider, LocalDateTime cutoff);
}
