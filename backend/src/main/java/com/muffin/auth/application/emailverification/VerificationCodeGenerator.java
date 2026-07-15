package com.muffin.auth.application.emailverification;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 이메일 인증번호(N자리 숫자) 생성기. */
@Component
@RequiredArgsConstructor
public class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationProperties properties;

    public String generate() {
        int codeLength = properties.codeLength();
        int bound = (int) Math.pow(10, codeLength);
        int value = RANDOM.nextInt(bound);
        return String.format("%0" + codeLength + "d", value);
    }
}
