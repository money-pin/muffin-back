package com.muffin.global.batch;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 배치 잡 실행을 감싸 <b>실행 1회 = 로그 한 줄</b>로 통일한다.
 *
 * <pre>
 * batch job=settlement date=2026-08-05 trigger=scheduler outcome=success duration_ms=1240 total=52 success=51 failed=1
 * batch job=open_price_collect date=2026-08-05 trigger=scheduler outcome=skipped duration_ms=8 reason=market_closed
 * batch job=rss_collection trigger=scheduler outcome=failure duration_ms=3011 error=SocketTimeoutException
 * </pre>
 *
 * <p>logfmt(공백으로 구분한 {@code key=value})을 쓰는 이유는 사람이 그대로 읽을 수 있으면서 Loki/CloudWatch Logs Insights/grep 어느
 * 쪽에서도 같은 규칙으로 파싱되기 때문이다. 이전에는 {@code BATCH-RSS completed: collected=1}과 {@code [settlement] triggered
 * ...}가 섞여 있어 균일 조회가 불가능했고, 시작만 찍고 끝을 안 찍는 잡이 많아 "돌긴 했는데 끝났는지"를 알 수 없었다.
 *
 * <p>예외는 여기서 잡아 삼킨다. 스프링의 기본 스케줄러 오류 처리기도 로그를 남기고 예외를 억제하므로 <b>동작은 종전과 같고 로그 형식만 통일된다.</b> 잡 하나의 실패가
 * 스케줄러 스레드를 죽여 이후 실행을 막지 않는다는 성질도 그대로다.
 *
 * <p>로거를 잡의 도메인 패키지({@link BatchJob#loggerName()})로 잡기 때문에, 러너가 {@code global} 패키지에 있어도 로그의 {@code domain}
 * 필드는 실제 도메인(investment, news 등)으로 남는다.
 *
 * <p>같은 지점에서 {@link BatchJobMetrics}로 지표도 발행한다. 로그와 지표의 발행 지점을 하나로 두어 <b>둘이 서로 다른 얘기를 하는 상황을 만들지 않는다.</b>
 */
@Component
public class BatchJobRunner {

    private static final String MESSAGE_PREFIX = "batch";

    /** 이 문자가 하나라도 있으면 값을 따옴표로 감싼다. 필드 경계(공백), 키값 구분자(=), 레코드 경계(개행/탭), 인용 문자(따옴표/역슬래시). */
    private static final String QUOTE_TRIGGERS = " =\"\\\r\n\t";

    private final BatchJobMetrics metrics;

    public BatchJobRunner(BatchJobMetrics metrics) {
        this.metrics = metrics;
    }

    /** 기준 일자가 없는 잡(주기적 정리 등)을 위한 축약형. */
    public void run(BatchJob job, BatchTrigger trigger, BatchJobCallback callback) {
        run(job, trigger, null, callback);
    }

    public void run(BatchJob job, BatchTrigger trigger, LocalDate businessDate, BatchJobCallback callback) {
        Logger log = LoggerFactory.getLogger(job.loggerName());
        long startedAt = System.nanoTime();
        try {
            BatchJobReport report = callback.execute();
            long durationMillis = elapsedMillis(startedAt);
            String line = reportedLine(job, trigger, businessDate, durationMillis, report);
            if (report.outcome() == BatchOutcome.FAILURE) {
                log.error(line);
            } else {
                log.info(line);
            }
            metrics.record(job, trigger, report.outcome(), durationMillis);
        } catch (Exception exception) {
            long durationMillis = elapsedMillis(startedAt);
            log.error(failureLine(job, trigger, businessDate, durationMillis, exception), exception);
            metrics.record(job, trigger, BatchOutcome.FAILURE, durationMillis);
        }
    }

    private String reportedLine(
            BatchJob job, BatchTrigger trigger, LocalDate businessDate, long durationMillis, BatchJobReport report) {
        StringBuilder line = header(job, trigger, businessDate, durationMillis, report.outcome());
        append(line, "reason", report.reason());
        for (Map.Entry<String, Object> detail : report.details().entrySet()) {
            append(line, detail.getKey(), detail.getValue());
        }
        return line.toString();
    }

    private String failureLine(
            BatchJob job, BatchTrigger trigger, LocalDate businessDate, long durationMillis, Exception exception) {
        StringBuilder line = header(job, trigger, businessDate, durationMillis, BatchOutcome.FAILURE);
        append(line, "error", exception.getClass().getSimpleName());
        return line.toString();
    }

    private StringBuilder header(
            BatchJob job, BatchTrigger trigger, LocalDate businessDate, long durationMillis, BatchOutcome outcome) {
        StringBuilder line = new StringBuilder(MESSAGE_PREFIX);
        append(line, "job", job.code());
        append(line, "date", businessDate);
        append(line, "trigger", trigger.code());
        append(line, "outcome", outcome.code());
        append(line, "duration_ms", durationMillis);
        return line;
    }

    /** 값이 {@code null}이면 키 자체를 생략한다. 빈 값을 남기면 파싱 규칙만 복잡해지고 알려주는 것이 없다. */
    private void append(StringBuilder line, String key, Object value) {
        if (value == null) {
            return;
        }
        line.append(' ').append(key).append('=').append(quoteIfNeeded(String.valueOf(value)));
    }

    /**
     * logfmt는 공백으로 필드를, 개행으로 레코드를 나눈다. 그 경계를 무너뜨릴 문자가 섞인 값만 따옴표로 감싸고 이스케이프한다.
     *
     * <p>개행과 탭까지 다루는 이유는 <b>"실행 1회 = 로그 한 줄"이 이 규칙의 전부이기 때문</b>이다. 값 하나에 개행이 들어가면 한 실행이 여러 줄로 쪼개져 파싱도
     * 집계도 어긋난다. 역슬래시를 가장 먼저 이스케이프하지 않으면 값 끝의 {@code \}가 닫는 따옴표를 잡아먹는다.
     */
    private String quoteIfNeeded(String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        if (!needsQuoting(value)) {
            return value;
        }
        return '"'
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n")
                        .replace("\t", "\\t")
                + '"';
    }

    private boolean needsQuoting(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (QUOTE_TRIGGERS.indexOf(value.charAt(index)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
