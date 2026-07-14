package com.muffin.sector.infrastructure.toss.exception;

import java.time.Duration;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/** 토스증권 API 레이트리밋(429)을 최대 재시도 횟수까지 초과했을 때 발생한다. */
@Getter
public class TossRateLimitExceededException extends TossApiException {

    private final Duration retryAfter;

    public TossRateLimitExceededException(
            String requestId, String tossCode, HttpStatusCode httpStatus, String message, Duration retryAfter) {
        super(requestId, tossCode, httpStatus, message);
        this.retryAfter = retryAfter;
    }
}
