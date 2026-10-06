package com.muffin.briefing.application.generation;

/** 브리핑 생성 시도의 결과. 배치 로그와 재시도 판단에 쓴다. */
public record BriefingGenerationSummary(Outcome outcome, int issueCount) {

    public enum Outcome {
        /** 브리핑을 만들어 발행 대기 상태로 저장했다. */
        GENERATED,
        /** 다른 인스턴스가 이미 예약했거나 오늘 브리핑이 이미 있다. */
        ALREADY_RESERVED,
        /** 후보 뉴스가 최소 개수에 못 미쳐 아직 생성 시점이 아니다. */
        INSUFFICIENT_NEWS,
        /** 생성 또는 검증에 실패해 이용 불가로 저장했다. */
        FAILED
    }

    public static BriefingGenerationSummary of(Outcome outcome) {
        return new BriefingGenerationSummary(outcome, 0);
    }

    public static BriefingGenerationSummary generated(int issueCount) {
        return new BriefingGenerationSummary(Outcome.GENERATED, issueCount);
    }
}
