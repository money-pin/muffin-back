package com.muffin.investment.domain.userasset;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** UserAsset 애그리거트 리포지토리 */
public interface UserAssetRepository extends JpaRepository<UserAsset, Long> {

    // TODO : 확인필요 - 이슈 #23 총자산 조회를 위해 기존 Repository에 사용자 ID 조회를 추가함.
    Optional<UserAsset> findByUserId(Long userId);

    // TODO : 확인필요 - 이슈 #40 동시 투자 확정과 자정 NO_INVEST 생성을 직렬화하기 위해 기존 UserAsset 행을 잠금 조회함.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ua from UserAsset ua where ua.userId = :userId")
    Optional<UserAsset> findByUserIdForUpdate(@Param("userId") Long userId);

    // TODO : 확인필요 - 이슈 #40 자정 마감 대상과 사용자별 잠금을 위해 기존 UserAsset Repository 조회를 확장함.
    List<UserAsset> findByCreatedAtBefore(LocalDateTime cutoff);

    // TODO : 확인필요 - 이슈 #40 날짜별 마감 완료 여부를 판정하기 위해 기존 UserAsset Repository에 대상 건수 조회를 추가함.
    long countByCreatedAtBefore(LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ua from UserAsset ua where ua.id = :id")
    Optional<UserAsset> findByIdForUpdate(@Param("id") Long id);
}
