package com.muffin.user.application.onboarding;

import com.muffin.auth.application.ConstraintViolations;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.exception.UserException;
import com.muffin.user.domain.exception.code.UserErrorCode;
import com.muffin.user.presentation.onboarding.dto.OnboardingCompleteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 온보딩 완료 확인 및 초기 투자금 지급 유스케이스. 이미 지급된 사용자가 다시 호출해도 기존 자산을 그대로 반환한다(멱등). */
@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingCompletionService {

    private static final long INITIAL_ASSET = 1_000_000L;
    private static final String USER_ASSET_UNIQUE_CONSTRAINT = "uk_user_asset_user";

    private final UserRepository userRepository;
    private final UserAssetRepository userAssetRepository;
    private final InitialAssetGranter initialAssetGranter;

    public OnboardingCompleteResponse complete(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        if (!user.isOnboardingCompleted()) {
            throw new UserException(UserErrorCode.ONBOARDING_NOT_COMPLETED);
        }

        UserAsset asset = ensureInitialAssetGranted(userId);
        return new OnboardingCompleteResponse(asset.getTotalAsset());
    }

    /**
     * 온보딩 완료(캐릭터 확정) 시점에 지급을 시도하지만, 그 순간 실패하더라도 이 API가 다시 호출되면 없는 경우에만
     * 보정 지급한다(멱등).
     */
    public UserAsset ensureInitialAssetGranted(Long userId) {
        return userAssetRepository.findByUserId(userId).orElseGet(() -> grantInitialAsset(userId));
    }

    private UserAsset grantInitialAsset(Long userId) {
        try {
            return initialAssetGranter.grant(userId, INITIAL_ASSET);
        } catch (DataIntegrityViolationException e) {
            // 중복 호출 레이스: 커밋 전 동시 요청이 먼저 지급을 마쳤을 수 있다. 다른 무결성 위반까지 여기서 삼키지 않도록
            // 실제 위반 제약을 확인한 뒤에만 기존 자산을 재조회해 멱등 응답으로 되돌린다.
            if (!ConstraintViolations.isConstraint(e, USER_ASSET_UNIQUE_CONSTRAINT)) {
                throw e;
            }
            return userAssetRepository.findByUserId(userId).orElseThrow(() -> e);
        }
    }
}
