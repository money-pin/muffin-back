package com.muffin.sector.infrastructure.toss;

import com.muffin.sector.infrastructure.toss.dto.TossErrorResponse;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import com.muffin.sector.infrastructure.toss.exception.TossRateLimitExceededException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 토스증권 API 호출의 공통 처리(레이트리밋, 재시도, 에러 변환)를 담당한다. 실제 요청 구성(URI, 헤더, 바디)은 호출부가 넘기는
 * {@link Supplier}가 맡으므로, 이 클래스는 어떤 엔드포인트를 부르는지 알 필요가 없다.
 */
@Slf4j
@Component
public class TossApiClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);
    private static final Duration TRANSIENT_ERROR_BACKOFF_UNIT = Duration.ofMillis(500);
    private static final long JITTER_UPPER_BOUND_MILLIS = 100;

    private final TossRateLimiter rateLimiter;

    public TossApiClient(TossRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public <T> T execute(Supplier<T> request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            rateLimiter.acquire();
            try {
                return request.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                handleTooManyRequests(e, attempt);
            } catch (RestClientResponseException e) {
                throw toApiException(e);
            } catch (ResourceAccessException e) {
                handleTransientNetworkError(e, attempt);
            }
        }
        throw new IllegalStateException("도달할 수 없는 코드입니다.");
    }

    private void handleTooManyRequests(HttpClientErrorException.TooManyRequests e, int attempt) {
        TossErrorResponse.TossError error = readError(e);
        if (attempt == MAX_ATTEMPTS) {
            throw new TossRateLimitExceededException(
                    requestIdOf(error), codeOf(error), e.getStatusCode(), e.getMessage(), retryAfter(e));
        }
        log.warn("토스증권 API 레이트리밋 초과, 재시도합니다. requestId={}, code={}", requestIdOf(error), codeOf(error));
        sleep(withJitter(retryAfter(e)));
    }

    private void handleTransientNetworkError(ResourceAccessException e, int attempt) {
        if (attempt == MAX_ATTEMPTS) {
            throw new TossApiException("토스증권 API 호출에 실패했습니다.", e);
        }
        log.warn("토스증권 API 호출 중 네트워크 오류, 재시도합니다. attempt={}", attempt);
        sleep(TRANSIENT_ERROR_BACKOFF_UNIT.multipliedBy(attempt));
    }

    private TossApiException toApiException(RestClientResponseException e) {
        TossErrorResponse.TossError error = readError(e);
        log.warn("토스증권 API 호출 실패. requestId={}, code={}", requestIdOf(error), codeOf(error));
        return new TossApiException(requestIdOf(error), codeOf(error), e.getStatusCode(), e.getMessage());
    }

    private TossErrorResponse.TossError readError(RestClientResponseException e) {
        try {
            TossErrorResponse response = e.getResponseBodyAs(TossErrorResponse.class);
            return response == null ? null : response.error();
        } catch (RuntimeException parseException) {
            return null;
        }
    }

    private String requestIdOf(TossErrorResponse.TossError error) {
        return error == null ? null : error.requestId();
    }

    private String codeOf(TossErrorResponse.TossError error) {
        return error == null ? null : error.code();
    }

    private Duration retryAfter(HttpClientErrorException.TooManyRequests e) {
        String header =
                e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null) {
            return DEFAULT_RETRY_AFTER;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException ex) {
            return DEFAULT_RETRY_AFTER;
        }
    }

    private Duration withJitter(Duration base) {
        return base.plusMillis(ThreadLocalRandom.current().nextLong(0, JITTER_UPPER_BOUND_MILLIS));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
