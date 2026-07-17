package com.muffin.auth.application.google;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.DeletedEmail;
import com.muffin.auth.domain.DeletedEmailRepository;
import com.muffin.auth.domain.GoogleIdTokenPayload;
import com.muffin.auth.domain.GoogleIdTokenVerifier;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구글 OAuth 통합 유스케이스. ID Token을 검증한 뒤 Auth(GOOGLE, sub)가 있으면 로그인, 없으면 termsAgreed를
 * 검사해 자동 가입한다(가입/로그인을 별도 엔드포인트로 나누지 않음). 구글이 이미 이메일을 검증했으므로
 * Auth.createGoogle이 emailVerified=true로 시작한다.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long DELETED_EMAIL_BLOCK_DAYS = 30;

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final DeletedEmailRepository deletedEmailRepository;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;

    @Transactional
    public TokenPair authenticate(String idToken, boolean termsAgreed) {
        GoogleIdTokenPayload payload = googleIdTokenVerifier.verify(idToken);

        Auth auth = authRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, payload.sub())
                .orElseGet(() -> signup(payload, termsAgreed));

        User user = userRepository
                .findById(auth.getUserId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new GeneralException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new GeneralException(AuthErrorCode.SUSPENDED_ACCOUNT);
        }

        String accessToken =
                accessTokenProvider.issue(user.getUserId(), user.getRole().name());
        String refreshToken = refreshTokenIssuer.issue(user.getUserId());
        return new TokenPair(accessToken, refreshToken);
    }

    private Auth signup(GoogleIdTokenPayload payload, boolean termsAgreed) {
        if (!termsAgreed) {
            throw new GeneralException(AuthErrorCode.TERMS_NOT_AGREED);
        }
        LocalDateTime cutoff = LocalDateTime.now(KST).minusDays(DELETED_EMAIL_BLOCK_DAYS);
        if (deletedEmailRepository.existsByEmailHashAndDeletedAtAfter(DeletedEmail.hash(payload.email()), cutoff)) {
            throw new GeneralException(AuthErrorCode.RECENTLY_DELETED_EMAIL);
        }

        User user = User.register(null, UUID.randomUUID().toString(), payload.name(), null);
        user.agreeToTerms();
        userRepository.save(user);

        Auth auth = Auth.createGoogle(user.getUserId(), payload.email(), payload.sub());
        try {
            return authRepository.saveAndFlush(auth);
        } catch (DataIntegrityViolationException e) {
            // findByProviderAndProviderUserId 통과 이후 커밋 전 동시 가입 레이스: DB unique 제약이 최종 방어선.
            throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }
    }
}
