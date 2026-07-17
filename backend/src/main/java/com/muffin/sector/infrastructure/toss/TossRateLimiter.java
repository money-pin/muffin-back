package com.muffin.sector.infrastructure.toss;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** 토스증권 API 호출 간 최소 간격을 보장하는 간단한 레이트리미터. ETF 11개 규모의 호출량에 맞춘 최소 구현이다. */
public class TossRateLimiter {

    static final Duration DEFAULT_MIN_INTERVAL = Duration.ofMillis(350);

    private final Clock clock;
    private final Duration minInterval;
    private final Sleeper sleeper;
    private Instant lastCallAt;

    public TossRateLimiter(Clock clock) {
        this(clock, DEFAULT_MIN_INTERVAL);
    }

    public TossRateLimiter(Clock clock, Duration minInterval) {
        this(clock, minInterval, TossRateLimiter::sleep);
    }

    TossRateLimiter(Clock clock, Duration minInterval, Sleeper sleeper) {
        this.clock = clock;
        this.minInterval = minInterval;
        this.sleeper = sleeper;
    }

    /** 이전 호출과의 간격이 최소 간격보다 짧으면 그 차이만큼 대기한 뒤 리턴한다. */
    public synchronized void acquire() {
        Instant now = clock.instant();
        if (lastCallAt != null) {
            Duration waitTime = minInterval.minus(Duration.between(lastCallAt, now));
            if (waitTime.isPositive()) {
                sleeper.sleep(waitTime);
            }
        }
        lastCallAt = clock.instant();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("레이트리밋 대기 중 인터럽트가 발생했습니다.", e);
        }
    }

    @FunctionalInterface
    interface Sleeper {

        void sleep(Duration duration);
    }
}
