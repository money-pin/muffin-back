package com.muffin.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * 구조화 로그(JSON)에 {@code domain} 필드를 추가한다. Grafana에서 도메인별로 로그를 걸러 보고 색을 입히는 기준이 된다.
 *
 * <p>도메인은 <b>로거 이름에서 자동으로 추출</b>한다({@code com.muffin.investment.presentation.SettlementScheduler} →
 * {@code investment}). MDC에 손으로 넣는 방식을 쓰지 않는 이유는 두 가지다. 첫째, 태그를 넣어야 할 지점을 사람이 빠뜨릴 수 있다. 둘째, MDC는 스레드에 매인 값이라
 * 비동기 실행이나 이벤트 리스너로 넘어갈 때 조용히 사라지거나 남아 오염된다. 패키지 구조가 이미 도메인별로 갈려 있으므로 로거 이름이 더 신뢰할 수 있는 출처다.
 *
 * <p>{@code com.muffin} 밖의 로거(프레임워크, 라이브러리)는 도메인이 없으므로 필드를 아예 넣지 않는다. 값이 있는 줄만 도메인 로그로 취급된다.
 *
 * <p>ANSI 색상 코드는 넣지 않는다. 제어문자가 JSON 문자열에 섞이면 Loki 검색이 깨진다. 색은 이 필드를 보고 Grafana가 칠한다.
 */
public class DomainJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    static final String FIELD_NAME = "domain";

    private static final String ROOT_PACKAGE = "com.muffin.";

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add(FIELD_NAME, (JsonWriter.Extractor<ILoggingEvent, String>) event -> domainOf(event.getLoggerName()))
                .whenHasLength();
    }

    /**
     * 로거 이름에서 도메인 패키지명을 뽑는다.
     *
     * @return {@code com.muffin.<domain>....} 형태면 {@code <domain>}, 아니면 {@code null}
     */
    static String domainOf(String loggerName) {
        if (loggerName == null || !loggerName.startsWith(ROOT_PACKAGE)) {
            return null;
        }
        int start = ROOT_PACKAGE.length();
        int end = loggerName.indexOf('.', start);
        if (end < 0) {
            return null;
        }
        String domain = loggerName.substring(start, end);
        return domain.isEmpty() ? null : domain;
    }
}
