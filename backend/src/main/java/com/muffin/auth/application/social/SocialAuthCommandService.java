package com.muffin.auth.application.social;

import com.muffin.auth.application.ConstraintViolations;
import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.SocialIdTokenVerifier;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedaccount.DeletedAccountRepository;
import com.muffin.auth.domain.deletedaccount.IdentityHasher;
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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 통합 유스케이스. provider별 ID Token을 검증한 뒤 Auth(provider, sub)가 있으면 로그인, 없으면 자동 가입한다
 * (가입/로그인을 별도 엔드포인트로 나누지 않음). provider 자체 동의 화면을 거쳐 로그인했다고 보고 서비스 약관도 자동 동의로
 * 간주한다. 계정은 provider+sub로만 식별하므로, 다른 계정과 이메일이 같아도 별개 계정으로 가입된다.
 */
@Service
@RequiredArgsConstructor
public class SocialAuthCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long REJOIN_BLOCK_DAYS = 30;

    private final List<SocialIdTokenVerifier> verifiers;
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final DeletedAccountRepository deletedAccountRepository;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final EntityManager entityManager;
    private final IdentityHasher identityHasher;

    @Transactional
    public TokenPair authenticate(AuthProvider provider, String idToken) {
        SocialIdentity identity = verifierFor(provider).verify(idToken);

        Auth auth = authRepository
                .findByProviderAndProviderUserId(provider, identity.sub())
                .orElseGet(() -> signup(provider, identity));

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

    /**
     * 목록에서 매번 찾는다. 기동 시점에 Map으로 만들어 두면, 테스트가 verifier를 목으로 바꿨을 때 아직 스텁되지 않은
     * provider()가 null을 돌려줘 컨텍스트 기동이 깨진다. verifier는 provider 수(최대 3개)만큼이라 비용은 무시할 수준이다.
     */
    private SocialIdTokenVerifier verifierFor(AuthProvider provider) {
        return verifiers.stream()
                .filter(verifier -> verifier.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ID Token 검증기가 등록되지 않은 provider입니다: " + provider));
    }

    private Auth signup(AuthProvider provider, SocialIdentity identity) {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusDays(REJOIN_BLOCK_DAYS);
        String identityHash = identityHasher.hash(provider, identity.sub());
        if (deletedAccountRepository.existsByIdentityHashAndDeletedAtAfter(identityHash, cutoff)) {
            throw new AuthException(AuthErrorCode.RECENTLY_WITHDRAWN_ACCOUNT);
        }

        User user = User.register(null, UUID.randomUUID().toString(), User.truncateName(identity.name()), null);
        user.agreeToTerms();
        userRepository.save(user);

        Auth auth = Auth.createSocial(user.getUserId(), provider, identity.email(), identity.sub());
        try {
            return authRepository.saveAndFlush(auth);
        } catch (DataIntegrityViolationException e) {
            // 실패한 flush로 영속성 컨텍스트가 오염돼 있어(재조회 시 재플러시 시도로 깨짐), 재조회 전에 반드시 비워야 한다.
            entityManager.clear();

            if (ConstraintViolations.isConstraint(e, "uk_provider_user")) {
                // findByProviderAndProviderUserId 통과 이후 커밋 전에 같은 소셜 계정으로 먼저 가입을 완료한 요청이 있었던
                // 것: 남과 충돌한 게 아니라 나 자신의 동시 요청이므로, 실패시키지 않고 승자의 결과를 재조회해 로그인으로 처리한다.
                // User는 IDENTITY 생성 전략이라 save() 시점에 이미 INSERT가 끝나 있어(Auth와 달리 이 트랜잭션 롤백에 기대어
                // 자동으로 없어지지 않음), 짝을 잃은 이 요청 자신의 User 행을 직접 지워야 고아로 남지 않는다.
                userRepository.deleteById(user.getUserId());
                return authRepository
                        .findByProviderAndProviderUserId(provider, identity.sub())
                        .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN));
            }
            throw e;
        }
    }
}
