package com.muffin.news.application.reconstruction;

import java.util.List;

/**
 * 뉴스 재구성 결과. {@code title}은 매경 원문 제목을 대체할 AI 생성 제목이며, 검증에 실패하면 원문 제목이 그대로 유지된다({@link
 * com.muffin.news.domain.news.News#completeReconstruction}).
 */
public record NewsReconstructionResult(
        String title,
        String summary,
        String rewrittenBody,
        List<SectorImpactResult> sectorImpacts,
        List<String> warningFlags) {

    /** 뉴스 재구성 결과를 생성하고 경고 플래그 목록의 불변성을 보장한다. */
    public NewsReconstructionResult {
        if (sectorImpacts == null || warningFlags == null) {
            throw new IllegalArgumentException("sectorImpacts and warningFlags must not be null");
        }
        sectorImpacts = List.copyOf(sectorImpacts);
        warningFlags = List.copyOf(warningFlags);
    }
}
