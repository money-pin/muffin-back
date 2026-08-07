package com.muffin.auth.application.google;

import com.muffin.auth.application.ConstraintViolations;
import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.GoogleIdTokenPayload;
import com.muffin.auth.domain.GoogleIdTokenVerifier;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedemail.DeletedEmailRepository;
import com.muffin.auth.domain.deletedemail.EmailHasher;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구글 OAuth 통합 유스케이스. ID Token을 검증한 뒤 Auth(GOOGLE, sub)가 있으면 로그인, 없으면 자동 가입한다(가입/로그인을
 * 별도 엔드포인트로 나누지 않음). 구글 자체 동의 화면을 거쳐 로그인했다고 보고 서비스 약관도 자동 동의로 간주한다.
 * 구글이 이미 이메일을 검증했으므로 Auth.createGoogle이 emailVerified=true로 시작한다.
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
    private final EntityManager entityManager;
    private final EmailHasher emailHasher;

    @Transactional
    public TokenPair authenticate(String idToken) {
        GoogleIdTokenPayload payload = googleIdTokenVerifier.verify(idToken);

        Auth auth = authRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, payload.sub())
                .orElseGet(() -> signup(payload));

        User user = userRepository
                .findById(auth.getUserId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new AuthException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthException(AuthErrorCode.SUSPENDED_ACCOUNT);
        }

        String accessToken =
                accessTokenProvider.issue(user.getUserId(), user.getRole().name());
        String refreshToken = refreshTokenIssuer.issue(user.getUserId());
        return new TokenPair(accessToken, refreshToken);
    }

    private Auth signup(GoogleIdTokenPayload payload) {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusDays(DELETED_EMAIL_BLOCK_DAYS);
        if (deletedEmailRepository.existsByEmailHashAndDeletedAtAfter(emailHasher.hash(payload.email()), cutoff)) {
            throw new AuthException(AuthErrorCode.RECENTLY_DELETED_EMAIL);
        }

        User user = User.register(null, UUID.randomUUID().toString(), User.truncateName(payload.name()), null);
        user.agreeToTerms();
        userRepository.save(user);

        Auth auth = Auth.createGoogle(user.getUserId(), payload.email(), payload.sub());
        try {
            return authRepository.saveAndFlush(auth);
        } catch (DataIntegrityViolationException e) {
            // 실패한 flush로 영속성 컨텍스트가 오염돼 있어(재조회 시 재플러시 시도로 깨짐), 재조회 전에 반드시 비워야 한다.
            entityManager.clear();

            if (ConstraintViolations.isConstraint(e, "uk_provider_user")) {
                // findByProviderAndProviderUserId 통과 이후 커밋 전에 같은 구글 계정으로 먼저 가입을 완료한 요청이 있었던
                // 것: 남과 충돌한 게 아니라 나 자신의 동시 요청이므로, 실패시키지 않고 승자의 결과를 재조회해 로그인으로 처리한다.
                // User는 IDENTITY 생성 전략이라 save() 시점에 이미 INSERT가 끝나 있어(Auth와 달리 이 트랜잭션 롤백에 기대어
                // 자동으로 없어지지 않음), 짝을 잃은 이 요청 자신의 User 행을 직접 지워야 고아로 남지 않는다.
                userRepository.deleteById(user.getUserId());
                return authRepository
                        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, payload.sub())
                        .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN));
            }
            if (ConstraintViolations.isConstraint(e, "uk_provider_email")) {
                // 다른 구글 계정(sub)이 이미 같은 이메일을 쓰고 있음: 이건 진짜 충돌이라 그대로 실패시킨다.
                throw new AuthException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
            }
            throw e;
        }
    }
}
