package com.muffin.investment.domain.userasset;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** UserAsset 애그리거트 리포지토리 */
public interface UserAssetRepository extends JpaRepository<UserAsset, Long> {

    // TODO : 확인필요 - 이슈 #23 총자산 조회를 위해 기존 Repository에 사용자 ID 조회를 추가함.
    Optional<UserAsset> findByUserId(Long userId);
}
