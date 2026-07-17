package com.muffin.sector.infrastructure.coingecko;

import com.muffin.sector.infrastructure.coingecko.exception.CoinGeckoApiException;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * CoinGecko API 호출의 공통 처리(재시도, 에러 변환)를 담당한다. 실제 요청 구성(URI, 헤더)은 호출부가 넘기는
 * {@link Supplier}가 맡으므로, 이 클래스는 어떤 엔드포인트를 부르는지 알 필요가 없다.
 *
 * <p>토스증권 클라이언트({@code TossApiClient})와 달리 하루 한 번의 단발 호출만 필요해 별도 레이트리미터는 두지 않는다.
 */
@Slf4j
@Component
public class CoinGeckoApiClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(1);
    private static final Duration TRANSIENT_ERROR_BACKOFF_UNIT = Duration.ofMillis(500);

    public <T> T execute(Supplier<T> request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return request.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                handleTooManyRequests(e, attempt);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().is5xxServerError()) {
                    handleTransientServerError(e, attempt);
                } else {
                    throw toApiException(e);
                }
            } catch (ResourceAccessException e) {
                handleTransientNetworkError(e, attempt);
            }
        }
        throw new IllegalStateException("도달할 수 없는 코드입니다.");
    }

    private void handleTooManyRequests(HttpClientErrorException.TooManyRequests e, int attempt) {
        if (attempt == MAX_ATTEMPTS) {
            throw toApiException(e);
        }
        log.warn("CoinGecko API 레이트리밋 초과, 재시도합니다. attempt={}", attempt);
        sleep(retryAfter(e));
    }

    private void handleTransientNetworkError(ResourceAccessException e, int attempt) {
        if (attempt == MAX_ATTEMPTS) {
            throw new CoinGeckoApiException("CoinGecko API 호출에 실패했습니다.", e);
        }
        log.warn("CoinGecko API 호출 중 네트워크 오류, 재시도합니다. attempt={}", attempt);
        sleep(TRANSIENT_ERROR_BACKOFF_UNIT.multipliedBy(attempt));
    }

    private void handleTransientServerError(RestClientResponseException e, int attempt) {
        if (attempt == MAX_ATTEMPTS) {
            throw toApiException(e);
        }
        log.warn("CoinGecko API 서버 오류, 재시도합니다. status={}, attempt={}", e.getStatusCode(), attempt);
        sleep(TRANSIENT_ERROR_BACKOFF_UNIT.multipliedBy(attempt));
    }

    private CoinGeckoApiException toApiException(RestClientResponseException e) {
        log.warn("CoinGecko API 호출 실패. status={}", e.getStatusCode());
        return new CoinGeckoApiException(e.getStatusCode(), e.getMessage());
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

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
