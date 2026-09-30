package com.muffin.briefing.application.generation;

/** 브리핑 문장을 생성한다. 한 줄 요약과 이슈 3건이 같은 맥락에서 나와야 일관되므로 한 번의 호출로 전부 만든다. */
public interface BriefingGenerator {

    /**
     * 후보와 계산된 숫자를 받아 브리핑 문장을 만든다.
     *
     * @throws com.muffin.briefing.application.exception.BriefingGenerationException 응답이 규격을 벗어난 경우
     */
    BriefingGenerationResult generate(BriefingGenerationRequest request);
}
