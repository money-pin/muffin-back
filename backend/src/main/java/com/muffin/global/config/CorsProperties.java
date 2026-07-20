package com.muffin.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.cors.*} 설정값 바인딩. 프론트엔드 배포/로컬 origin을 환경별로 다르게 주입한다. */
@ConfigurationProperties(prefix = "muffin.cors")
public record CorsProperties(List<String> allowedOrigins) {}
