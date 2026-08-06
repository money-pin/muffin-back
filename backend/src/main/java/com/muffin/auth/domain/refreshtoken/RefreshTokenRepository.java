package com.muffin.auth.domain.refreshtoken;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** RefreshToken 애그리거트 리포지토리 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByUserId(Long userId);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    // 미인증 계정 정리 배치용
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken r where r.userId in :userIds")
    int deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
