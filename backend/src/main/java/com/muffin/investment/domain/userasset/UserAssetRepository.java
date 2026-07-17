package com.muffin.investment.domain.userasset;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** UserAsset 애그리거트 리포지토리 */
public interface UserAssetRepository extends JpaRepository<UserAsset, Long> {
    Optional<UserAsset> findByUserId(Long userId);
}
