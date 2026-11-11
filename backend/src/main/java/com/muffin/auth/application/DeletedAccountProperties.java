package com.muffin.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.deleted-account.*} 설정값 바인딩. */
@ConfigurationProperties(prefix = "muffin.auth.deleted-account")
public record DeletedAccountProperties(String hashSecret) {}
