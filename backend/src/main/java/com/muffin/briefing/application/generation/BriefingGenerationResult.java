package com.muffin.briefing.application.generation;

import java.util.List;

/**
 * 브리핑 생성 결과.
 *
 * @param headline 오늘의 한 줄
 * @param issues 이슈 3건
 * @param termId 오늘의 용어. 후보가 없었으면 null
 * @param termSummary 용어 뜻 한 줄. 후보가 없었으면 null
 */
public record BriefingGenerationResult(String headline, List<IssueResult> issues, Long termId, String termSummary) {

    public BriefingGenerationResult {
        issues = List.copyOf(issues);
    }

    /**
     * @param newsId 근거 뉴스. 반드시 요청에 담긴 후보 중 하나여야 한다
     * @param impactLine "그래서 나한테는?" 한 줄
     */
    public record IssueResult(Long newsId, String title, String summary, String impactLine) {}
}
