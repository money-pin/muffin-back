package com.muffin.briefing.application.generation;

import java.time.LocalDate;
import java.util.List;

/**
 * 브리핑 생성 요청. 숫자(섹터 등락률)는 이미 계산된 상태로 들어오고, AI는 문장만 쓴다.
 *
 * @param candidates 이슈로 고를 수 있는 뉴스 후보
 * @param buzzSignal 외부 매체 화제도. 비어 있을 수 있으며, 그때는 AI 판단만으로 이슈를 고른다
 * @param sectorLines 계산이 끝난 섹터 성적표. 비어 있을 수 있다
 * @param termCandidates 오늘의 용어 후보. 비어 있으면 용어 블록 없이 생성한다
 */
public record BriefingGenerationRequest(
        LocalDate briefingDate,
        List<BriefingIssueCandidate> candidates,
        BuzzSignal buzzSignal,
        List<BriefingSectorLine> sectorLines,
        List<BriefingTermCandidate> termCandidates) {

    public BriefingGenerationRequest {
        candidates = List.copyOf(candidates);
        sectorLines = List.copyOf(sectorLines);
        termCandidates = List.copyOf(termCandidates);
        buzzSignal = buzzSignal == null ? BuzzSignal.empty() : buzzSignal;
    }
}
