package com.muffin.news.infrastructure.retry;

import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 외부 요청에 공통으로 적용할 최대 3회, 고정 2초 간격의 재시도 정책을 실행한다. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetryExecutor {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2_000L;

    /** 전달받은 판정 조건에 해당하는 예외가 발생하면 요청을 최대 3회까지 고정 2초 간격으로 재시도한다. */
    public static <T> T execute(
            String operation,
            String target,
            String interruptionMessage,
            Supplier<T> request,
            Predicate<RuntimeException> retryableException) {
        int attempt = 1;
        while (true) {
            try {
                return request.get();
            } catch (RuntimeException exception) {
                if (!retryableException.test(exception) || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
                waitBeforeRetry(operation, target, interruptionMessage, attempt, exception);
                attempt++;
            }
        }
    }

    /** 재시도 정보를 기록하고 다음 요청 전에 고정 2초 동안 대기한다. */
    private static void waitBeforeRetry(
            String operation, String target, String interruptionMessage, int attempt, RuntimeException cause) {
        log.warn(
                "{} failed; retrying: target={}, attempt={}/{}, delayMs={}",
                operation,
                target,
                attempt,
                MAX_ATTEMPTS,
                RETRY_DELAY_MS,
                cause);
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruptionMessage, exception);
        }
    }
}
