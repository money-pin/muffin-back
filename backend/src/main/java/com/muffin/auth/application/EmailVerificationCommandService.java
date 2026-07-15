package com.muffin.auth.application;

import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.domain.DuplicateEmailException;
import com.muffin.auth.domain.DuplicateEmailValidator;
import com.muffin.auth.domain.EmailVerification;
import com.muffin.auth.domain.EmailVerificationRepository;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.global.apiPayload.exception.GeneralException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이메일 인증번호 발송/확인 유스케이스. */
@Service
@RequiredArgsConstructor
public class EmailVerificationCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final EmailVerificationRepository emailVerificationRepository;
    private final DuplicateEmailValidator duplicateEmailValidator;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationWriter emailVerificationWriter;
    private final EmailVerificationProperties properties;

    /**
     * 인증번호를 생성해 저장하고 메일로 발송한다. 이미 가입된 이메일, 쿨다운 중, 일일 발송 한도 초과 시 거부한다.
     */
    public void sendCode(String email) {
        try {
            duplicateEmailValidator.validate(email);
        } catch (DuplicateEmailException e) {
            throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }

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
    }

    /**
     * 가장 최근 발송된 인증번호와 대조한다. 불일치 시 시도 횟수를 누적하고, 한도 초과 시 이후 요청은 잠금 처리한다.
     *
     * <p>noRollbackFor 필수: 불일치 시 increaseAttemptCount() 이후 바로 GeneralException을 던지는데, 기본 롤백 정책대로면
     * 이 카운트 증가 자체가 커밋되지 않아 잠금(isLocked)이 무력화되고 무제한 브루트포스가 가능해진다.
     */
    @Transactional(noRollbackFor = GeneralException.class)
    public void verifyCode(String email, String code) {
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
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
    }
}
