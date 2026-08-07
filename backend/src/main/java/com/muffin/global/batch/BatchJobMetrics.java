package com.muffin.global.batch;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 배치 잡 실행을 지표로 발행한다. 로그가 "무슨 일이 있었나"를 답한다면 지표는 "지금 정상인가"를 답한다.
 *
 * <p>핵심은 {@link #LAST_SUCCESS_METRIC}이다. 알림 하나(<code>time() - last_success &gt; 26h</code>)로 실패·예외사망·미발화·cron
 * 오설정·컨테이너 다운이 <b>전부</b> 잡힌다. 실패를 세는 방식은 "잡이 아예 안 돌았다"를 못 잡지만, 마지막 성공 시각은 침묵 자체를 신호로 바꾼다.
 *
 * <p>{@link BatchOutcome#SKIPPED}도 성공 시각을 갱신한다. 휴장일이나 이미 처리된 날에 잡이 아무것도 하지 않는 것은 정상 동작이고, 이걸 갱신하지 않으면 연휴마다
 * 알림이 울린다.
 *
 * <p><b>게이지는 인메모리라 재기동하면 0으로 돌아간다.</b> 0은 "이번 기동 이후 성공 기록 없음"을 뜻하며, 알림 룰이 이 값을 그대로 쓰면 배포 직후 오탐이 난다. DB에서
 * 시드할지 룰에서 다룰지는 대시보드/알림 작업에서 정한다.
 */
@Component
public class BatchJobMetrics {

    static final String DURATION_METRIC = "muffin.batch.job.duration";
    static final String LAST_SUCCESS_METRIC = "muffin.batch.job.last.success.timestamp";

    private static final String JOB_TAG = "job";
    private static final String TRIGGER_TAG = "trigger";
    private static final String OUTCOME_TAG = "outcome";

    private final MeterRegistry registry;
    private final Clock clock;
    private final Map<BatchJob, AtomicLong> lastSuccessByJob = new EnumMap<>(BatchJob.class);

    public BatchJobMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        for (BatchJob job : BatchJob.values()) {
            lastSuccessByJob.put(job, registerLastSuccessGauge(job));
        }
    }

    /** 실행 1회를 기록한다. 로그 한 줄과 같은 시점에 호출된다. */
    public void record(BatchJob job, BatchTrigger trigger, BatchOutcome outcome, long durationMillis) {
        registry.timer(DURATION_METRIC, JOB_TAG, job.code(), TRIGGER_TAG, trigger.code(), OUTCOME_TAG, outcome.code())
                .record(durationMillis, TimeUnit.MILLISECONDS);

        if (outcome != BatchOutcome.FAILURE) {
            lastSuccessByJob.get(job).set(clock.instant().getEpochSecond());
        }
    }

    /**
     * 모든 잡의 게이지를 기동 시점에 등록한다. 첫 실행 때 등록하면 <b>한 번도 돌지 않은 잡의 시계열이 아예 없어</b> 대시보드에서 빈 자리가 "정상"인지 "잡이 사라진
     * 것"인지 구분되지 않는다.
     */
    private AtomicLong registerLastSuccessGauge(BatchJob job) {
        AtomicLong lastSuccessEpochSeconds = new AtomicLong();
        Gauge.builder(LAST_SUCCESS_METRIC, lastSuccessEpochSeconds, AtomicLong::doubleValue)
                .tag(JOB_TAG, job.code())
                .description("마지막으로 정상 종료(성공 또는 스킵)한 시각. 0이면 이번 기동 이후 성공 기록이 없다.")
                .baseUnit("seconds")
                .register(registry);
        return lastSuccessEpochSeconds;
    }
}
