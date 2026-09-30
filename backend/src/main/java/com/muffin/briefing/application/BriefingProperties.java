package com.muffin.briefing.application;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브리핑 생성·조회 설정.
 *
 * @param minCandidateNews 이슈를 고를 후보 뉴스의 최소 개수. 이보다 적으면 아직 생성 시점이 아니라고 보고 예약을 해제한다
 * @param recentDays 다시 보기로 제공할 최근 브리핑 편수
 */
@ConfigurationProperties(prefix = "muffin.briefing")
public record BriefingProperties(int minCandidateNews, int recentDays, Buzz buzz) {

    public BriefingProperties {
        minCandidateNews = minCandidateNews > 0 ? minCandidateNews : 8;
        recentDays = recentDays > 0 ? recentDays : 7;
        buzz = buzz == null ? new Buzz(false, List.of(), 0) : buzz;
    }

    /**
     * @param enabled 외부 화제도 신호 사용 여부. 꺼도 브리핑은 생성된다
     * @param queries Google News에 던질 검색어
     * @param timeoutSeconds 조회 타임아웃. 아침 배치를 오래 붙잡지 않도록 짧게 둔다
     */
    public record Buzz(boolean enabled, List<String> queries, int timeoutSeconds) {

        public Buzz {
            queries = queries == null ? List.of() : List.copyOf(queries);
            timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 10;
        }
    }
}
