package com.muffin.auth.application.emailverification;

import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.emailverification.EmailVerification;
import com.muffin.auth.domain.emailverification.EmailVerificationRepository;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일 인증번호 발송/확인 유스케이스. 인증된 사용자 본인의 Auth.email을 대상으로만 동작한다. */
@Service
@RequiredArgsConstructor
public class EmailVerificationCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AuthRepository authRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationWriter emailVerificationWriter;
    private final EmailVerificationProperties properties;

    /**
     * 인증번호를 생성해 저장하고 메일로 발송한다. 이미 인증 완료된 계정, 쿨다운 중, 일일 발송 한도 초과 시 거부한다.
     *
     * @return 인증번호 만료까지 남은 시간(초)
     */
    public long sendCode(Long userId) {
        Auth auth = getAuth(userId);
        if (auth.isEmailVerified()) {
            throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        String email = auth.getEmail();

        emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .filter(latest -> latest.isWithinCooldown(properties.resendCooldownSeconds()))
                .ifPresent(latest -> {
                    throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_RESEND_COOLDOWN);
                });

        LocalDateTime startOfToday = LocalDate.now(KST).atStartOfDay();
        long todayCount = emailVerificationRepository.countByEmailAndCreatedAtAfter(email, startOfToday);
        if (todayCount >= properties.maxDailyResendCount()) {
            throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED);
        }

        String code = emailVerificationWriter.save(email);
        emailSender.sendVerificationCode(email, code);

        return properties.expireMinutes() * 60;
    }

    /**
     * 가장 최근 발송된 인증번호와 대조한다. 불일치 시 시도 횟수를 누적하고, 한도 초과 시 이후 요청은 잠금 처리한다. 성공 시 같은
     * 트랜잭션에서 Auth.emailVerified도 함께 true로 바꾼다.
     *
     * <p>noRollbackFor 필수: 불일치 시 increaseAttemptCount() 이후 바로 GeneralException을 던지는데, 기본 롤백 정책대로면
     * 이 카운트 증가 자체가 커밋되지 않아 잠금(isLocked)이 무력화되고 무제한 브루트포스가 가능해진다.
     */
    @Transactional(noRollbackFor = GeneralException.class)
    public void verifyCode(Long userId, String code) {
        Auth auth = getAuth(userId);
        if (auth.isEmailVerified()) {
            throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        String email = auth.getEmail();

        EmailVerification verification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDescForUpdate(email)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH));

        if (verification.isLocked(properties.maxAttempts())) {
            throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_LOCKED);
        }
        if (verification.isExpired()) {
            throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            verification.increaseAttemptCount();
            throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        verification.markVerified();
        auth.verifyEmail();
    }

    private Auth getAuth(Long userId) {
        return authRepository.findByUserId(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND));
    }
}
