package com.muffin.global.batch;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchJobMetricsTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:35:00Z");

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final BatchJobMetrics metrics = new BatchJobMetrics(registry, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

    @Test
    @DisplayName("한 번도 실행되지 않은 잡도 기동 시점에 게이지가 등록돼 대시보드에서 빈 자리가 생기지 않는다")
    void construct_registersGaugeForEveryJob() {
        assertThat(registry.find(BatchJobMetrics.LAST_SUCCESS_METRIC).gauges()).hasSize(BatchJob.values().length);
        assertThat(lastSuccess(BatchJob.SETTLEMENT)).isZero();
    }

    @Test
    @DisplayName("성공하면 마지막 성공 시각이 현재 시각으로 갱신된다")
    void record_updatesLastSuccessOnSuccess() {
        metrics.record(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BatchOutcome.SUCCESS, 1_240);

        assertThat(lastSuccess(BatchJob.SETTLEMENT)).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    @DisplayName("휴장일 스킵도 정상 동작이므로 마지막 성공 시각을 갱신한다")
    void record_updatesLastSuccessOnSkip() {
        metrics.record(BatchJob.OPEN_PRICE_COLLECT, BatchTrigger.SCHEDULER, BatchOutcome.SKIPPED, 8);

        assertThat(lastSuccess(BatchJob.OPEN_PRICE_COLLECT)).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    @DisplayName("선행 조건 미충족으로 미룬 것은 마지막 성공 시각을 갱신하지 않는다")
    void record_leavesLastSuccessOnDeferral() {
        metrics.record(BatchJob.WEEKLY_RANKING, BatchTrigger.SCHEDULER, BatchOutcome.DEFERRED, 15);

        assertThat(lastSuccess(BatchJob.WEEKLY_RANKING)).isZero();
    }

    @Test
    @DisplayName("미룸이 하루 넘게 반복돼도 마지막 성공 시각은 그대로여서 침묵이 쌓인다")
    void record_doesNotRefreshLastSuccessWhileDeferring() {
        // 게이지는 이름+태그로 식별되므로 같은 레지스트리에 두 번 등록하면 먼저 등록된 것이 유지된다.
        // 다른 시계를 쓰려면 레지스트리도 분리해야 한다.
        MeterRegistry isolated = new SimpleMeterRegistry();
        MutableClock clock = new MutableClock(NOW);
        BatchJobMetrics metrics = new BatchJobMetrics(isolated, clock);

        metrics.record(BatchJob.WEEKLY_RANKING, BatchTrigger.SCHEDULER, BatchOutcome.SUCCESS, 100);
        double afterSuccess = lastSuccess(isolated, BatchJob.WEEKLY_RANKING);

        for (int attempt = 0; attempt < 5; attempt++) {
            clock.advance(Duration.ofHours(6));
            metrics.record(BatchJob.WEEKLY_RANKING, BatchTrigger.SCHEDULER, BatchOutcome.DEFERRED, 15);
        }

        assertThat(afterSuccess).isEqualTo(NOW.getEpochSecond());
        assertThat(lastSuccess(isolated, BatchJob.WEEKLY_RANKING)).isEqualTo(afterSuccess);
    }

    @Test
    @DisplayName("실패는 마지막 성공 시각을 갱신하지 않아 침묵이 알림으로 이어진다")
    void record_leavesLastSuccessOnFailure() {
        metrics.record(BatchJob.RSS_COLLECTION, BatchTrigger.SCHEDULER, BatchOutcome.FAILURE, 3_011);

        assertThat(lastSuccess(BatchJob.RSS_COLLECTION)).isZero();
    }

    @Test
    @DisplayName("소요시간은 잡·트리거·결과별로 나뉘어 기록된다")
    void record_tagsDurationByJobTriggerAndOutcome() {
        metrics.record(BatchJob.SETTLEMENT, BatchTrigger.EVENT, BatchOutcome.SUCCESS, 1_240);
        metrics.record(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BatchOutcome.SKIPPED, 12);

        Timer eventTimer = registry.find(BatchJobMetrics.DURATION_METRIC)
                .tags("job", "settlement", "trigger", "event", "outcome", "success")
                .timer();
        assertThat(eventTimer).isNotNull();
        assertThat(eventTimer.count()).isEqualTo(1);
        assertThat(eventTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1_240);

        assertThat(registry.find(BatchJobMetrics.DURATION_METRIC)
                        .tags("job", "settlement", "trigger", "scheduler", "outcome", "skipped")
                        .timer())
                .isNotNull();
    }

    private double lastSuccess(BatchJob job) {
        return lastSuccess(registry, job);
    }

    private static double lastSuccess(MeterRegistry registry, BatchJob job) {
        Gauge gauge = registry.find(BatchJobMetrics.LAST_SUCCESS_METRIC)
                .tag("job", job.code())
                .gauge();
        assertThat(gauge).isNotNull();
        return gauge.value();
    }

    /**
     * 시간이 실제로 흐르는 시계. 고정 시계로는 "갱신하지 않았다"를 검증할 수 없다. 구현이 잘못 갱신해도 같은 값이 들어가 테스트가 통과해 버리기 때문이다.
     */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
