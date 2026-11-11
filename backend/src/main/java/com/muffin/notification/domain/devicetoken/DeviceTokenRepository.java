package com.muffin.notification.domain.devicetoken;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    @Query("select d.id from DeviceToken d where d.token = :token")
    Optional<Long> findIdByToken(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeviceToken d where d.id = :id")
    Optional<DeviceToken> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("delete from DeviceToken d where d.expiresAt is not null and d.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    List<DeviceToken> findAllByUserId(Long userId);

    void deleteByUserId(Long userId);

    // 이 기기의 바뀌기 전 토큰을 지운다. 같은 토큰 행(token <>)과, 곧 로그아웃될 다른 기기의 토큰(expiresAt 있음)은 남긴다.
    @Modifying
    @Query("delete from DeviceToken d where d.userId = :userId and d.token <> :token and d.expiresAt is null")
    void deleteOtherTokensOf(@Param("userId") Long userId, @Param("token") String token);

    /** 다른 기기에서 로그인하면, 그때까지 쓰던 기기의 토큰에 알림을 받을 마지막 시각(그 기기가 로그아웃되는 시각)을 적는다. */
    @Modifying
    @Query("update DeviceToken d set d.expiresAt = :endsAt where d.userId = :userId and d.expiresAt is null")
    void endCurrentTokensAt(@Param("userId") Long userId, @Param("endsAt") LocalDateTime endsAt);
}
