package com.muffin.global.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관측이 관측 대상을 오염시키지 않는지 확인한다.
 *
 * <p>지표 발행과 잡 실행이 한 블록에 있으면, 지표 쪽이 터졌을 때 <b>성공한 배치가 실패로 기록되고</b> 실패 처리 경로에서 다시 터지면 예외가 스케줄러까지 새어나간다.
 * 관측을 붙인 대가로 배치가 죽는 셈이라, 실행과 관측의 분리를 테스트로 못박는다.
 */
class BatchJobRunnerObservationTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 8);

    @Test
    @DisplayName("지표 발행이 실패해도 성공한 배치는 성공으로 남고 예외가 밖으로 나가지 않는다")
    void run_keepsSuccessWhenMetricRecordingFails() {
        BatchJobRunner runner = new BatchJobRunner(new ExplodingMetrics());

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.SETTLEMENT)) {
            assertThatCode(() -> runner.run(
                            BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, () -> BatchJobReport.success()
                                    .with("total", 52)))
                    .doesNotThrowAnyException();

            assertThat(capture.line()).contains("batch metric record failed", "job=settlement");
            assertThat(capture.level()).isEqualTo(Level.WARN);
        }
    }

    @Test
    @DisplayName("잡이 실패한 뒤 지표 발행까지 실패해도 예외가 스케줄러로 전파되지 않는다")
    void run_swallowsMetricFailureOnFailedJob() {
        BatchJobRunner runner = new BatchJobRunner(new ExplodingMetrics());

        assertThatCode(() -> runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, () -> {
                    throw new IllegalStateException("db down");
                }))
                .doesNotThrowAnyException();
    }

    /** 지표 레지스트리가 고장 난 상황을 흉내 낸다. */
    private static final class ExplodingMetrics extends BatchJobMetrics {

        private ExplodingMetrics() {
            super(registry(), Clock.systemDefaultZone());
        }

        private static MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Override
        public void record(BatchJob job, BatchTrigger trigger, BatchOutcome outcome, long durationMillis) {
            throw new IllegalStateException("metric backend unavailable");
        }
    }
}
