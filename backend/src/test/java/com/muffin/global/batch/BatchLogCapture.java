package com.muffin.global.batch;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * 배치 실행 로그 한 줄을 잡아 검증하기 위한 테스트 헬퍼.
 *
 * <p>스케줄러가 도메인 결과를 어떤 {@code outcome}/{@code reason}으로 옮기는지는 로그 한 줄에만 드러난다. 그 매핑이 대시보드와 알림의 입력이라 값이
 * 바뀌면 관측이 조용히 깨지므로, 로그 문자열 자체를 검증 대상으로 삼는다.
 */
public final class BatchLogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private BatchLogCapture(BatchJob job) {
        this.logger = (Logger) LoggerFactory.getLogger(job.loggerName());
        this.appender.start();
        this.logger.addAppender(appender);
    }

    public static BatchLogCapture on(BatchJob job) {
        return new BatchLogCapture(job);
    }

    /** 마지막으로 남은 배치 로그 한 줄. */
    public String line() {
        return event().getFormattedMessage();
    }

    public Level level() {
        return event().getLevel();
    }

    private ILoggingEvent event() {
        assertThat(appender.list).as("배치 로그가 남지 않았다").isNotEmpty();
        return appender.list.getLast();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
