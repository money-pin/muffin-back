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
 * 알림이 울린다. 반면 {@link BatchOutcome#DEFERRED}는 갱신하지 않는다. 선행 조건이 안 갖춰져 <b>못 한</b> 것이라, 갱신해 버리면 산출물이 하나도 안
 * 나오는데도 재시도할 때마다 성공 시각이 새로 찍혀 알림이 영원히 울리지 않는다.
 *
 * <p><b>게이지는 인메모리라 재기동하면 0으로 돌아간다.</b> 0은 "이번 기동 이후 성공 기록 없음"을 뜻한다. 알림 룰이 이 값을 그대로 쓰면 배포 직후 오탐이 나므로,
 * 룰에서 {@code > 0} 조건으로 거르고 그 사이 생기는 무감시 구간은 {@code process_start_time_seconds}를 쓰는 짝 룰이 메운다(DB 시드는 하지 않는다.
 * 잡마다 소스가 달라 14개를 각각 설계해야 하는데, 얻는 것은 재기동 직후 잠깐의 정확도뿐이다). 룰 정의는
 * <code>infra/monitoring/alerting/rules.yaml</code>에 있다.
 */
@Component
public class BatchJobMetrics {

    static final String DURATION_METRIC = "muffin.batch.job.duration";
    static final String LAST_SUCCESS_METRIC = "muffin.batch.job.last.success.timestamp";

    /**
     * 잡 이름 태그. <b>{@code job}이 아니라 {@code batch_job}인 이유가 있다.</b> {@code job}은 프로메테우스가 스크레이프 대상을 식별하는 데 쓰는
     * 예약 라벨이고, 수집기 설정(<code>infra/monitoring/alloy/config.alloy</code>)도 이 앱에 {@code job="muffin-backend"}를 붙인다.
     * 앱이 같은 이름의 라벨을 내보내면 충돌해서, 프로메테우스가 앱 쪽 값을 {@code exported_job}으로 밀어낸다. 그러면 대시보드와 알림 룰을 전부
     * {@code exported_job=}으로 써야 하고, 왜 그런지 아는 사람만 읽을 수 있는 쿼리가 된다.
     *
     * <p>배치 로그 한 줄({@link BatchJobRunner})은 {@code job=} 그대로 쓴다. Loki 스트림 라벨에는 {@code job}이 없어 충돌하지 않고, 로그 규격은
     * 0단계에서 정해 문서에 박아 둔 외부 계약이라 이유 없이 흔들지 않는다.
     */
    private static final String JOB_TAG = "batch_job";

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

        if (outcome == BatchOutcome.SUCCESS || outcome == BatchOutcome.SKIPPED) {
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
