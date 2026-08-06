package com.muffin.auth.application.withdraw;

import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedemail.DeletedEmail;
import com.muffin.auth.domain.deletedemail.DeletedEmailRepository;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 유스케이스(즉시 처리 범위). 이름은 User.withdraw()가 즉시 지우고, 이메일은 복구 불가능한 값으로 대체한 뒤 원본의
 * 해시만 DeletedEmail에 남겨 재가입 제한(30일)에 쓴다. refresh token은 즉시 무효화한다.
 *
 * <p>투자/퀴즈 기록의 비식별화·삭제는 별도 배치(6개월 후)에서 처리하며 여기서 다루지 않는다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawCommandService {

    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final DeletedEmailRepository deletedEmailRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
        Auth auth =
                authRepository.findByUserId(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));

        user.withdraw();
        deletedEmailRepository.save(DeletedEmail.of(auth.getEmail()));
        auth.anonymizeEmail();
        refreshTokenRepository.deleteByUserId(userId);
    }
}
