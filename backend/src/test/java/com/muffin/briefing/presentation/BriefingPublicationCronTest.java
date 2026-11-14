package com.muffin.briefing.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

/**
 * 브리핑 발행 크론이 주말에도 발화하는지 검증한다.
 *
 * <p>크론 문자열을 테스트에 다시 적지 않고 {@code application.yml}에서 읽는다. 운영은 이 기본값을 그대로 쓰므로(발행 크론을
 * 덮어쓰는 환경변수가 없다), 설정이 바뀌면 이 테스트가 같이 움직여야 한다.
 *
 * <p>생성 스케줄러는 요일 제한이 없어 주말에도 브리핑을 만든다. 발행만 평일로 묶여 있으면 주말에 만들어진 브리핑이 READY로 남아
 * 사용자에게 영영 보이지 않는다.
 */
class BriefingPublicationCronTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 2026-11-14는 토요일, 11-15는 일요일, 11-16은 월요일이다. */
    private static final LocalDateTime SATURDAY = LocalDateTime.of(2026, 11, 14, 0, 0);

    private static final LocalDateTime SUNDAY = LocalDateTime.of(2026, 11, 15, 0, 0);
    private static final LocalDateTime MONDAY = LocalDateTime.of(2026, 11, 16, 0, 0);

    @Test
    @DisplayName("발행 크론은 토요일 07:30에 발화한다")
    void publicationCron_firesOnSaturday() {
        assertThat(nextFireAfter(publicationCron(), SATURDAY))
                .isEqualTo(SATURDAY.withHour(7).withMinute(30));
    }

    @Test
    @DisplayName("발행 크론은 일요일 07:30에 발화한다")
    void publicationCron_firesOnSunday() {
        assertThat(nextFireAfter(publicationCron(), SUNDAY))
                .isEqualTo(SUNDAY.withHour(7).withMinute(30));
    }

    @Test
    @DisplayName("평일 발행 시각은 종전과 같다")
    void publicationCron_keepsWeekdaySchedule() {
        assertThat(nextFireAfter(publicationCron(), MONDAY))
                .isEqualTo(MONDAY.withHour(7).withMinute(30));
    }

    /** 07:50까지 공개하지 못한 브리핑을 늦게라도 내보내는 경로도 주말에 열려 있어야 한다. */
    @Test
    @DisplayName("따라잡기 크론도 주말에 발화한다")
    void catchUpCron_firesOnWeekend() {
        assertThat(nextFireAfter(catchUpCron(), SATURDAY))
                .isEqualTo(SATURDAY.withHour(8).withMinute(0));
        assertThat(nextFireAfter(catchUpCron(), SUNDAY))
                .isEqualTo(SUNDAY.withHour(8).withMinute(0));
    }

    private static LocalDateTime nextFireAfter(String cron, LocalDateTime from) {
        ZonedDateTime next = CronExpression.parse(cron).next(from.atZone(KST));
        return next == null ? null : next.toLocalDateTime();
    }

    private static String publicationCron() {
        return cronFromYaml("muffin.batch.briefing-publication.cron");
    }

    private static String catchUpCron() {
        return cronFromYaml("muffin.batch.briefing-publication.catch-up-cron");
    }

    /** 플레이스홀더 기본값({@code ${VAR:default}})만 떼어 실제로 쓰이는 크론 문자열을 얻는다. */
    private static String cronFromYaml(String key) {
        String raw = yamlValue(key);
        if (raw.startsWith("${") && raw.endsWith("}")) {
            String inside = raw.substring(2, raw.length() - 1);
            return inside.substring(inside.indexOf(':') + 1);
        }
        return raw;
    }

    private static String yamlValue(String key) {
        try {
            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
            for (PropertySource<?> source : sources) {
                Object value = source.getProperty(key);
                if (value != null) {
                    return value.toString();
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("application.yml에서 " + key + "를 읽지 못했습니다.", exception);
        }
        throw new IllegalStateException("application.yml에 " + key + "가 없습니다.");
    }
}
