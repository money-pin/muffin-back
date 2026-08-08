package com.muffin.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.deleted-email.*} 설정값 바인딩. */
@ConfigurationProperties(prefix = "muffin.auth.deleted-email")
public record DeletedEmailProperties(String hashSecret) {}
