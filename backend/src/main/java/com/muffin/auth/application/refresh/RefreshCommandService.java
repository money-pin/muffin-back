package com.muffin.auth.application.refresh;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.domain.refreshtoken.RefreshToken;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Access/Refresh Token 재발급 유스케이스. 재발급마다 refresh token도 함께 회전(rotate)한다. */
@Service
@RequiredArgsConstructor
public class RefreshCommandService {

    private final RefreshTokenIssuer refreshTokenIssuer;
    private final UserRepository userRepository;
    private final AccessTokenProvider accessTokenProvider;

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenIssuer
                .findValid(rawRefreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        User user = userRepository
                .findById(refreshToken.getUserId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new AuthException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthException(AuthErrorCode.SUSPENDED_ACCOUNT);
        }

        String accessToken =
                accessTokenProvider.issue(user.getUserId(), user.getRole().name());
        String newRefreshToken = refreshTokenIssuer.issue(user.getUserId());

        return new TokenPair(accessToken, newRefreshToken);
    }
}
