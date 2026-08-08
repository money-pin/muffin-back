package com.muffin.quiz.application.generation;

/**
 * 하루치 퀴즈 생성 1회 시도의 결말.
 *
 * <p>생성 서비스는 실패해도 예외를 밖으로 던지지 않는다. 조회 API가 UNAVAILABLE로 응답할 수 있도록 빈 퀴즈 세트를 저장하고 정상 종료한다. 그래서 호출자가 결말을
 * 알려면 반환값이 필요하다. <b>이게 없으면 생성이 매 시도 실패해도 배치 로그에는 성공으로 남아 알림이 울리지 않는다.</b>
 *
 * @param outcome 이번 시도가 무엇을 했는지
 * @param questionCount 생성한 문항 수. 생성하지 않았으면 0
 */
public record DailyQuizGenerationSummary(Outcome outcome, int questionCount) {

    public enum Outcome {
        /** 퀴즈 세트를 생성했다. */
        GENERATED,
        /** 이미 생성됐거나 다른 실행이 생성 중이라 이번 시도는 아무것도 하지 않았다. */
        ALREADY_RESERVED,
        /** 재구성이 끝난 뉴스가 아직 부족해 생성 시점이 아니다. */
        INSUFFICIENT_NEWS,
        /** 생성에 실패해 이용 불가 세트를 저장했다. */
        FAILED
    }

    static DailyQuizGenerationSummary of(Outcome outcome) {
        return new DailyQuizGenerationSummary(outcome, 0);
    }

    static DailyQuizGenerationSummary generated(int questionCount) {
        return new DailyQuizGenerationSummary(Outcome.GENERATED, questionCount);
    }
}
