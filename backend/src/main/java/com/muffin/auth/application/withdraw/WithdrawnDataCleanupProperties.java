package com.muffin.auth.application.withdraw;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.withdrawal.data-cleanup.*} 설정값 바인딩. */
@ConfigurationProperties(prefix = "muffin.auth.withdrawal.data-cleanup")
public record WithdrawnDataCleanupProperties(long retentionMonths) {}
