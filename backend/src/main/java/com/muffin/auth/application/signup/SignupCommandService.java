package com.muffin.auth.application.signup;

import com.muffin.auth.application.ConstraintViolations;
import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedemail.DeletedEmail;
import com.muffin.auth.domain.deletedemail.DeletedEmailRepository;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로컬(이메일/비밀번호) 회원가입 유스케이스. 가입 즉시 로그인 상태로 access/refresh token을 발급한다. */
@Service
@RequiredArgsConstructor
public class SignupCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long DELETED_EMAIL_BLOCK_DAYS = 30;

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final DeletedEmailRepository deletedEmailRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;

    @Transactional
    public TokenPair signupLocal(String email, String rawPassword, String name, boolean termsAgreed) {
        if (!termsAgreed) {
            throw new AuthException(AuthErrorCode.TERMS_NOT_AGREED);
        }
        LocalDateTime cutoff = LocalDateTime.now(KST).minusDays(DELETED_EMAIL_BLOCK_DAYS);
        if (deletedEmailRepository.existsByEmailHashAndDeletedAtAfter(DeletedEmail.hash(email), cutoff)) {
            throw new AuthException(AuthErrorCode.RECENTLY_DELETED_EMAIL);
        }
        if (authRepository.existsByEmail(email)) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }

        User user = User.register(null, UUID.randomUUID().toString(), name, null);
        user.agreeToTerms();
        userRepository.save(user);

        String encodedPassword = passwordEncoder.encode(rawPassword);
        Auth auth = Auth.createLocal(user.getUserId(), email, rawPassword, encodedPassword);
        try {
            authRepository.saveAndFlush(auth);
        } catch (DataIntegrityViolationException e) {
            // existsByEmail 이후 커밋 전 동시 가입 레이스: DB unique 제약(uk_provider_email)이 최종 방어선.
            // 다른 무결성 위반까지 이메일 중복으로 잘못 번역하지 않도록 실제 위반 제약을 확인한다.
            if (!ConstraintViolations.isConstraint(e, "uk_provider_email")) {
                throw e;
            }
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }

        String accessToken =
                accessTokenProvider.issue(user.getUserId(), user.getRole().name());
        String refreshToken = refreshTokenIssuer.issue(user.getUserId());

        return new TokenPair(accessToken, refreshToken);
    }
}
