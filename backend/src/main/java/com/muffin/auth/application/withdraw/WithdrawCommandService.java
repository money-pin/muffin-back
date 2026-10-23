package com.muffin.auth.application.withdraw;

import com.muffin.auth.application.social.SocialIdTokenVerifiers;
import com.muffin.auth.application.withdraw.AppleAuthorizationClient.AppleTokens;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedaccount.DeletedAccount;
import com.muffin.auth.domain.deletedaccount.DeletedAccountRepository;
import com.muffin.auth.domain.deletedaccount.IdentityHasher;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final AppleAuthorizationClient appleAuthorizationClient;
    private final SocialIdTokenVerifiers socialIdTokenVerifiers;
    private final TransactionTemplate transactionTemplate;

    /**
     * 애플 계정은 철회에 쓸 토큰을 받기 위해 appleAuthorizationCode가 필요하다. 애플 호출은 DB 커넥션을 잡지 않도록 트랜잭션
     * 밖에서 먼저 끝내고, 실패하면 아무것도 바꾸지 않는다.
     */
    public void withdraw(Long userId, String appleAuthorizationCode) {
        Auth auth =
                authRepository.findByUserId(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        String appleRefreshToken = auth.getProvider() == AuthProvider.APPLE
                ? verifyAppleAuthorization(auth, appleAuthorizationCode)
                : null;

        transactionTemplate.executeWithoutResult(status -> withdrawInTransaction(userId, appleRefreshToken));
    }

    /** code가 이 계정의 것인지 교환 결과의 idToken sub로 확인한다. 남의 애플 계정 code로 그 계정의 연결을 끊지 못하게 한다. */
    private String verifyAppleAuthorization(Auth auth, String appleAuthorizationCode) {
        if (appleAuthorizationCode == null || appleAuthorizationCode.isBlank()) {
            throw new AuthException(AuthErrorCode.APPLE_AUTHORIZATION_CODE_REQUIRED);
        }
        AppleTokens tokens = appleAuthorizationClient.exchange(appleAuthorizationCode);
        SocialIdentity identity = socialIdTokenVerifiers.verify(AuthProvider.APPLE, tokens.idToken());
        if (!identity.sub().equals(auth.getProviderUserId())) {
            throw new AuthException(AuthErrorCode.APPLE_ACCOUNT_MISMATCH);
        }
        return tokens.refreshToken();
    }

    private void withdrawInTransaction(Long userId, String appleRefreshToken) {
        User user = userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        Auth auth =
                authRepository.findByUserId(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        user.withdraw();
        // 익명화하면 Auth의 원본 sub가 덮어써지므로 미리 꺼내 둔다.
        String providerUserId = auth.getProviderUserId();
        deletedAccountRepository.save(DeletedAccount.of(identityHasher.hash(auth.getProvider(), providerUserId)));
        auth.anonymize();
        refreshTokenRepository.deleteByUserId(userId);

        eventPublisher.publishEvent(new AccountWithdrawnEvent(auth.getProvider(), providerUserId, appleRefreshToken));
    }
}
