package com.muffin.auth.domain;

import com.muffin.auth.domain.enums.AuthProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Auth 애그리거트 리포지토리 */
public interface AuthRepository extends JpaRepository<Auth, Long> {

    boolean existsByEmail(String email);

    Optional<Auth> findByUserId(Long userId);

    Optional<Auth> findByProviderAndEmail(AuthProvider provider, String email);

    // 미인증 계정 정리 배치용
    List<Auth> findAllByProviderAndEmailVerifiedFalseAndCreatedAtBefore(AuthProvider provider, LocalDateTime cutoff);
}
