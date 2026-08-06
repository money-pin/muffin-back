package com.muffin.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

class DomainJsonMembersCustomizerTest {

    private final DomainJsonMembersCustomizer customizer = new DomainJsonMembersCustomizer();

    @Test
    @DisplayName("도메인 패키지 아래 로거는 세 번째 조각을 도메인으로 갖는다")
    void domainOf_extractsDomainPackage() {
        assertThat(DomainJsonMembersCustomizer.domainOf("com.muffin.investment.presentation.SettlementScheduler"))
                .isEqualTo("investment");
        assertThat(DomainJsonMembersCustomizer.domainOf("com.muffin.news.batch"))
                .isEqualTo("news");
    }

    @Test
    @DisplayName("프로젝트 밖 로거는 도메인이 없다")
    void domainOf_returnsNullForForeignLogger() {
        assertThat(DomainJsonMembersCustomizer.domainOf("org.springframework.boot.SpringApplication"))
                .isNull();
        assertThat(DomainJsonMembersCustomizer.domainOf(null)).isNull();
    }

    @Test
    @DisplayName("도메인 패키지가 없는 최상위 로거는 도메인이 없다")
    void domainOf_returnsNullWithoutDomainSegment() {
        assertThat(DomainJsonMembersCustomizer.domainOf("com.muffin.MuffinApplication"))
                .isNull();
        assertThat(DomainJsonMembersCustomizer.domainOf("com.muffin.")).isNull();
    }

    @Test
    @DisplayName("도메인이 있는 로그에만 domain 필드가 실린다")
    void customize_writesDomainOnlyForProjectLoggers() {
        JsonWriter<ILoggingEvent> writer = JsonWriter.of(customizer::customize);

        assertThat(writer.writeToString(eventFrom("com.muffin.investment.presentation.SettlementScheduler")))
                .isEqualTo("{\"domain\":\"investment\"}");
        assertThat(writer.writeToString(eventFrom("org.hibernate.SQL"))).isEqualTo("{}");
    }

    private ILoggingEvent eventFrom(String loggerName) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName(loggerName);
        event.setLoggerContext(new LoggerContext());
        return event;
    }
}
