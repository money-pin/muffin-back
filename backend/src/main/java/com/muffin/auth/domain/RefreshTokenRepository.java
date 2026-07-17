package com.muffin.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** RefreshToken 애그리거트 리포지토리 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByUserId(Long userId);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);
}
