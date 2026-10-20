package com.muffin.auth.application.withdraw;

import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedaccount.DeletedAccount;
import com.muffin.auth.domain.deletedaccount.DeletedAccountRepository;
import com.muffin.auth.domain.deletedaccount.IdentityHasher;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 유스케이스(즉시 처리 범위). 이름은 User.withdraw()가 즉시 지우고, 소셜 계정 식별자(provider+sub)의 해시만
 * DeletedAccount에 남겨 재가입 제한(30일)에 쓰며 Auth의 sub와 이메일을 지운다. refresh token은 즉시 무효화한다.
 *
 * <p>투자/퀴즈 기록의 비식별화·삭제는 별도 배치(6개월 후)에서 처리하며 여기서 다루지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawCommandService {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final DeletedAccountRepository deletedAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final IdentityHasher identityHasher;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        Auth auth =
                authRepository.findByUserId(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        user.withdraw();
        // 익명화하면 Auth의 원본 sub가 덮어써지므로 미리 꺼내 둔다.
        String providerUserId = auth.getProviderUserId();
        deletedAccountRepository.save(DeletedAccount.of(identityHasher.hash(auth.getProvider(), providerUserId)));
        auth.anonymize();
        refreshTokenRepository.deleteByUserId(userId);

        eventPublisher.publishEvent(new AccountWithdrawnEvent(auth.getProvider(), providerUserId));
    }
}
