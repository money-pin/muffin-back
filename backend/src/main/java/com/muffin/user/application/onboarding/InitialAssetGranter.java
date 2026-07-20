package com.muffin.user.application.onboarding;

import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초기 자산 지급을 별도 물리 트랜잭션(REQUIRES_NEW)으로 격리한다. {@code saveAndFlush}가 unique 제약 위반으로
 * 실패하면 Hibernate 세션이 더 이상 사용할 수 없는 상태가 되어, 같은 트랜잭션에서 예외를 잡고 이어서 다른 쿼리를 실행해도
 * 커밋 시점에 {@code UnexpectedRollbackException}이 발생한다. 이 지급 로직만 별도 트랜잭션으로 분리해야 실패해도
 * 호출자의 트랜잭션(rollback-only로 오염되지 않음)에서 안전하게 기존 자산을 재조회할 수 있다.
 */
@Component
@RequiredArgsConstructor
class InitialAssetGranter {

    private final UserAssetRepository userAssetRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserAsset grant(Long userId, long initialAsset) {
        return userAssetRepository.saveAndFlush(UserAsset.create(userId, initialAsset));
    }
}
