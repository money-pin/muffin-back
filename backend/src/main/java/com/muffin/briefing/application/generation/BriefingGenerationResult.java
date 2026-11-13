package com.muffin.briefing.application.generation;

import java.util.List;

/**
 * 브리핑 생성 결과.
 *
 * @param headline 오늘의 한 줄
 * @param issues 이슈 3건
 * @param sectorReasons 섹터별 변동 이유. 성적표가 비어 있으면 빈 목록이다
 * @param termId 오늘의 용어. 후보가 없었으면 null
 * @param termSummary 용어 뜻 한 줄. 후보가 없었으면 null
 */
public record BriefingGenerationResult(
        String headline,
        List<IssueResult> issues,
        List<SectorReasonResult> sectorReasons,
        Long termId,
        String termSummary) {

    public BriefingGenerationResult {
        issues = List.copyOf(issues);
        sectorReasons = List.copyOf(sectorReasons);
    }

    /**
     * @param newsId 근거 뉴스. 반드시 요청에 담긴 후보 중 하나여야 한다
     * @param impactLine "그래서 나한테는?" 한 줄
     */
    public record IssueResult(Long newsId, String title, String summary, String impactLine) {}

    /**
     * 섹터 한 곳이 왜 그렇게 움직였는지 설명하는 1~2문장.
     *
     * <p>섹터를 id가 아니라 이름으로 식별하는 이유는 AI에게 내부 식별자를 노출하지 않기 때문이다. 프롬프트에는 섹터 이름만 들어가고,
     * 이름 목록은 JSON Schema의 {@code enum}으로 못박아 모델이 없는 섹터를 지어내지 못하게 한다.
     *
     * @param reason 설명 1~2문장. 등락률 숫자는 화면이 이미 보여주므로 문장에 다시 쓰지 않는다
     */
    public record SectorReasonResult(String sectorName, String reason) {}
}
