package com.muffin.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.email-verification.*} 설정값 바인딩. */
@ConfigurationProperties(prefix = "muffin.auth.email-verification")
public record EmailVerificationProperties(
        int codeLength, long expireMinutes, long resendCooldownSeconds, int maxAttempts, int maxDailyResendCount) {}
