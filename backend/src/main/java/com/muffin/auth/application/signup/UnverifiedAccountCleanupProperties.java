package com.muffin.auth.application.signup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.signup.unverified-account-cleanup.*} 설정값 바인딩. */
@ConfigurationProperties(prefix = "muffin.auth.signup.unverified-account-cleanup")
public record UnverifiedAccountCleanupProperties(long ttlMinutes) {}
