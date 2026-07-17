package com.muffin.auth.application.login;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로컬(이메일/비밀번호) 로그인 유스케이스. */
@Service
@RequiredArgsConstructor
public class LoginCommandService {

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenProvider accessTokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final LoginProperties loginProperties;

    /**
     * noRollbackFor 필수: 비밀번호 불일치 시 recordFailedLogin() 이후 바로 GeneralException을 던지는데, 기본
     * 롤백 정책대로면 이 실패 횟수 증가 자체가 커밋되지 않아 잠금이 무력화되고 무제한 브루트포스가 가능해진다
     * (EmailVerificationCommandService.verifyCode와 동일한 이유).
     */
    @Transactional(noRollbackFor = GeneralException.class)
    public TokenPair loginLocal(String email, String rawPassword) {
        // 가입되지 않은 이메일과 비밀번호 불일치를 같은 메시지로 응답해 계정 존재 여부가 노출되지 않게 한다.
        Auth auth = authRepository
                .findByProviderAndEmail(AuthProvider.LOCAL, email)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_CREDENTIALS));

        if (auth.isLoginLocked()) {
            throw new GeneralException(AuthErrorCode.LOGIN_LOCKED);
        }

        if (!passwordEncoder.matches(rawPassword, auth.getPasswordHash())) {
            auth.recordFailedLogin(loginProperties.maxAttempts(), loginProperties.lockMinutes());
            throw new GeneralException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        auth.resetLoginAttempts();

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
}
