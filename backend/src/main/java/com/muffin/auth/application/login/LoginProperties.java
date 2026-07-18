package com.muffin.auth.application.login;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.login.*} 설정값 바인딩. */
@ConfigurationProperties(prefix = "muffin.auth.login")
public record LoginProperties(int maxAttempts, long lockMinutes) {}
