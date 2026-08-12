package com.muffin.investment.domain.userasset;

import com.muffin.user.domain.enums.UserStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** UserAsset 애그리거트 리포지토리 */
public interface UserAssetRepository extends JpaRepository<UserAsset, Long> {

    Optional<UserAsset> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ua from UserAsset ua where ua.userId = :userId")
    Optional<UserAsset> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select ua from UserAsset ua "
            + "where ua.createdAt < :cutoff "
            + "and exists (select u.userId from User u "
            + "where u.userId = ua.userId and u.status = :status)")
    Slice<UserAsset> findByCreatedAtBeforeAndUserStatus(
            @Param("cutoff") LocalDateTime cutoff, @Param("status") UserStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ua from UserAsset ua where ua.id = :id")
    Optional<UserAsset> findByIdForUpdate(@Param("id") Long id);
}
