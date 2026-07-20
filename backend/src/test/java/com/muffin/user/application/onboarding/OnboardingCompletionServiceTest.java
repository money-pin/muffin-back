package com.muffin.user.application.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.presentation.onboarding.dto.OnboardingCompleteResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OnboardingCompletionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAssetRepository userAssetRepository;

    @Mock
    private InitialAssetGranter initialAssetGranter;

    @InjectMocks
    private OnboardingCompletionService onboardingCompletionService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.register(null, UUID.randomUUID().toString(), "홍길동", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("온보딩 미완료 → GeneralException(ONBOARDING_NOT_COMPLETED)")
    void onboardingNotCompleted() {
        assertThatThrownBy(() -> onboardingCompletionService.complete(USER_ID)).isInstanceOf(GeneralException.class);

        verify(initialAssetGranter, never())
                .grant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("온보딩 완료 + 신규 사용자 → 초기 자산 100만원 지급")
    void grantsInitialAsset() {
        user.completeOnboarding(1, 2, 3);
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(initialAssetGranter.grant(USER_ID, 1_000_000L)).thenReturn(UserAsset.create(USER_ID, 1_000_000L));

        OnboardingCompleteResponse response = onboardingCompletionService.complete(USER_ID);

        assertThat(response.totalAsset()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("이미 자산이 지급된 사용자 재호출 → 기존 자산 그대로 반환(멱등), 신규 지급 없음")
    void idempotentWhenAssetAlreadyExists() {
        user.completeOnboarding(1, 2, 3);
        UserAsset existing = UserAsset.create(USER_ID, 1_045_000L);
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        OnboardingCompleteResponse response = onboardingCompletionService.complete(USER_ID);

        assertThat(response.totalAsset()).isEqualTo(1_045_000L);
        verify(initialAssetGranter, never())
                .grant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("동시 중복 호출 레이스(unique 제약 위반) → 별도 트랜잭션(REQUIRES_NEW)에서만 실패하고, "
            + "호출자 트랜잭션은 오염되지 않아 기존 자산을 재조회해 멱등 응답으로 되돌린다")
    void racesToExistingAssetOnUniqueViolation() {
        user.completeOnboarding(1, 2, 3);
        UserAsset existing = UserAsset.create(USER_ID, 1_000_000L);
        when(userAssetRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(initialAssetGranter.grant(USER_ID, 1_000_000L))
                .thenThrow(new DataIntegrityViolationException("uk_user_asset_user"));

        OnboardingCompleteResponse response = onboardingCompletionService.complete(USER_ID);

        assertThat(response.totalAsset()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("관련 없는 무결성 위반은 그대로 전파한다")
    void rethrowsUnrelatedConstraintViolation() {
        user.completeOnboarding(1, 2, 3);
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(initialAssetGranter.grant(USER_ID, 1_000_000L))
                .thenThrow(new DataIntegrityViolationException("some_other_constraint"));

        assertThatThrownBy(() -> onboardingCompletionService.complete(USER_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
