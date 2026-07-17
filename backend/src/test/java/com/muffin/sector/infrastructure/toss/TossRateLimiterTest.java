package com.muffin.sector.infrastructure.toss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TossRateLimiterTest {

    @Test
    @DisplayName("기본 호출 간격은 3 TPS 미만인 350ms이다")
    void defaultInterval_isThreeHundredFiftyMilliseconds() {
        assertEquals(Duration.ofMillis(350), TossRateLimiter.DEFAULT_MIN_INTERVAL);
    }

    @Test
    @DisplayName("첫 호출은 대기하지 않는다")
    void acquire_firstCall_doesNotWait() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        TossRateLimiter rateLimiter = new TossRateLimiter(clock, Duration.ofSeconds(5), sleeper);

        rateLimiter.acquire();

        assertEquals(List.of(), sleeper.sleepDurations());
    }

    @Test
    @DisplayName("최소 간격 이내에 다시 호출하면 남은 시간만큼 대기한다")
    void acquire_withinMinInterval_waits() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        Duration minInterval = Duration.ofMillis(150);
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        TossRateLimiter rateLimiter = new TossRateLimiter(clock, minInterval, sleeper);

        rateLimiter.acquire();
        rateLimiter.acquire();

        assertEquals(List.of(minInterval), sleeper.sleepDurations());
    }

    @Test
    @DisplayName("최소 간격이 이미 지났다면 다시 호출해도 대기하지 않는다")
    void acquire_afterMinIntervalElapsed_doesNotWait() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-13T00:00:00Z"));
        Duration minInterval = Duration.ofMillis(150);
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        TossRateLimiter rateLimiter = new TossRateLimiter(clock, minInterval, sleeper);

        rateLimiter.acquire();
        clock.advance(minInterval.plusMillis(10));
        rateLimiter.acquire();

        assertEquals(List.of(), sleeper.sleepDurations());
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

    private static final class RecordingSleeper implements TossRateLimiter.Sleeper {

        private final MutableClock clock;
        private final List<Duration> sleepDurations = new ArrayList<>();

        private RecordingSleeper(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public void sleep(Duration duration) {
            sleepDurations.add(duration);
            clock.advance(duration);
        }

        List<Duration> sleepDurations() {
            return List.copyOf(sleepDurations);
        }
    }
}
