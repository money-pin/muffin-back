package com.muffin.global.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class BatchJobRunnerTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 5);

    private final BatchJobRunner runner = new BatchJobRunner();

    private Logger settlementLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        settlementLogger = (Logger) LoggerFactory.getLogger(BatchJob.SETTLEMENT.loggerName());
        appender = new ListAppender<>();
        appender.start();
        settlementLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        settlementLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("성공하면 잡·기준일·트리거·결과·소요시간과 보고된 수치가 한 줄에 순서대로 남는다")
    void run_logsSingleLineWithReportedDetails() {
        runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, () -> BatchJobReport.success()
                .with("total", 52)
                .with("success", 51)
                .with("failed", 1));

        assertThat(message())
                .matches("batch job=settlement date=2026-08-05 trigger=scheduler outcome=success duration_ms=\\d+ "
                        + "total=52 success=51 failed=1");
        assertThat(event().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("건너뛰면 outcome=skipped와 함께 이유가 남는다")
    void run_logsSkipReason() {
        runner.run(
                BatchJob.SETTLEMENT,
                BatchTrigger.EVENT,
                BUSINESS_DATE,
                () -> BatchJobReport.skipped("no_trading_signal"));

        assertThat(message()).contains("trigger=event", "outcome=skipped", "reason=no_trading_signal");
    }

    @Test
    @DisplayName("잡이 예외를 던지면 outcome=failure로 ERROR 한 줄을 남기고 예외를 전파하지 않는다")
    void run_swallowsExceptionAndLogsFailure() {
        assertThatCode(() -> runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, () -> {
                    throw new IllegalStateException("db down");
                }))
                .doesNotThrowAnyException();

        assertThat(message()).contains("outcome=failure", "error=IllegalStateException");
        assertThat(event().getLevel()).isEqualTo(Level.ERROR);
        assertThat(event().getThrowableProxy().getMessage()).isEqualTo("db down");
    }

    @Test
    @DisplayName("잡이 스스로 실패를 보고하면 예외가 없어도 outcome=failure로 ERROR를 남긴다")
    void run_logsReportedFailureAsError() {
        runner.run(
                BatchJob.SETTLEMENT,
                BatchTrigger.SCHEDULER,
                BUSINESS_DATE,
                () -> BatchJobReport.failure("generation_failed"));

        assertThat(message()).contains("outcome=failure", "reason=generation_failed");
        assertThat(event().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("기준 일자가 없는 잡은 date 필드를 아예 붙이지 않는다")
    void run_omitsDateWhenAbsent() {
        runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, () -> BatchJobReport.success());

        assertThat(message()).doesNotContain("date=").contains("job=settlement");
    }

    @Test
    @DisplayName("공백이 섞인 값은 따옴표로 감싸 필드 경계가 무너지지 않게 한다")
    void run_quotesValuesContainingSpaces() {
        runner.run(
                BatchJob.SETTLEMENT,
                BatchTrigger.SCHEDULER,
                BUSINESS_DATE,
                () -> BatchJobReport.skipped("market closed"));

        assertThat(message()).contains("reason=\"market closed\"");
    }

    @Test
    @DisplayName("개행과 탭이 섞인 값도 한 줄을 깨지 않도록 이스케이프한다")
    void run_escapesLineBreakingCharacters() {
        runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, () -> BatchJobReport.success()
                .with("note", "first\nsecond\tthird"));

        assertThat(message()).contains("note=\"first\\nsecond\\tthird\"").doesNotContain("\n", "\t");
    }

    @Test
    @DisplayName("역슬래시를 먼저 이스케이프해 값 끝의 역슬래시가 닫는 따옴표를 삼키지 않는다")
    void run_escapesBackslashBeforeQuote() {
        runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, () -> BatchJobReport.success()
                .with("path", "C:\\logs\\"));

        assertThat(message()).endsWith("path=\"C:\\\\logs\\\\\"");
    }

    @Test
    @DisplayName("보고서를 만들지 못해 null이 올라오면 성공이 아니라 실패로 남긴다")
    void run_treatsNullReportAsFailure() {
        runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, () -> null);

        assertThat(message()).contains("outcome=failure", "error=NullPointerException");
        assertThat(event().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("잡의 도메인 패키지 로거로 남겨 로그의 domain 필드가 global이 아닌 실제 도메인이 되게 한다")
    void run_logsThroughDomainLogger() {
        runner.run(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BUSINESS_DATE, BatchJobReport::success);

        assertThat(event().getLoggerName()).isEqualTo("com.muffin.investment.batch");
    }

    private ILoggingEvent event() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst();
    }

    private String message() {
        return event().getFormattedMessage();
    }
}
