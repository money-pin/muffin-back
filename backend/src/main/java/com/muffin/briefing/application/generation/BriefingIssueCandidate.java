package com.muffin.briefing.application.generation;

import java.util.List;

/**
 * 브리핑 이슈로 쓸 수 있는 뉴스 후보. AI는 이 목록 안에서만 고를 수 있다.
 *
 * @param keyTerms 해설카드의 핵심어. 같은 사건이 여러 후보로 들어왔는지 판단하는 단서로 쓴다
 */
public record BriefingIssueCandidate(
        Long newsId, String title, String summary, String categoryName, List<String> keyTerms) {

    public BriefingIssueCandidate {
        keyTerms = keyTerms == null ? List.of() : List.copyOf(keyTerms);
    }
}
