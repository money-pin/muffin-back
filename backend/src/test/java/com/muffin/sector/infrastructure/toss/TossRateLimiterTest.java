package com.muffin.sector.infrastructure.toss;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TossRateLimiterTest {

    @Test
    @DisplayName("첫 호출은 대기하지 않는다")
    void acquire_firstCall_doesNotWait() {
        TossRateLimiter rateLimiter = new TossRateLimiter(Clock.systemUTC(), Duration.ofSeconds(5));

        long elapsedMillis = elapsedMillis(rateLimiter::acquire);

        assertTrue(elapsedMillis < 200, "첫 호출은 거의 즉시 반환되어야 합니다. elapsed=" + elapsedMillis);
    }

    @Test
    @DisplayName("최소 간격 이내에 다시 호출하면 남은 시간만큼 대기한다")
    void acquire_withinMinInterval_waits() {
        Duration minInterval = Duration.ofMillis(150);
        TossRateLimiter rateLimiter = new TossRateLimiter(Clock.systemUTC(), minInterval);

        rateLimiter.acquire();
        long elapsedMillis = elapsedMillis(rateLimiter::acquire);

        assertTrue(elapsedMillis >= minInterval.toMillis(), "최소 간격만큼 대기해야 합니다. elapsed=" + elapsedMillis);
    }

    @Test
    @DisplayName("최소 간격이 이미 지났다면 다시 호출해도 대기하지 않는다")
    void acquire_afterMinIntervalElapsed_doesNotWait() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        Duration minInterval = Duration.ofMillis(150);
        TossRateLimiter rateLimiter = new TossRateLimiter(clock, minInterval);

        rateLimiter.acquire();
        clock.advance(minInterval.plusMillis(10));
        long elapsedMillis = elapsedMillis(rateLimiter::acquire);

        assertTrue(elapsedMillis < 100, "간격이 지났다면 대기하지 않아야 합니다. elapsed=" + elapsedMillis);
    }

    private long elapsedMillis(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
