package com.muffin.notification.domain.devicetoken;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    Optional<DeviceToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    // 사용자의 옛 토큰만 지운다. token <> 조건으로 같은 토큰을 다시 보낸 경우의 행은 남긴다.
    // 벌크 삭제라 즉시 실행되어, 이어지는 주인 이동이 user_id 유니크에 걸리지 않는다.
    @Modifying
    @Query("delete from DeviceToken d where d.userId = :userId and d.token <> :token")
    void deleteOtherTokensOf(@Param("userId") Long userId, @Param("token") String token);
}
