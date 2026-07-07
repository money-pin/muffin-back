package com.muffin.investment.domain.userAsset;

import org.springframework.data.jpa.repository.JpaRepository;

/** UserAsset 애그리거트 리포지토리 */
public interface UserAssetRepository extends JpaRepository<UserAsset, Long> {}
