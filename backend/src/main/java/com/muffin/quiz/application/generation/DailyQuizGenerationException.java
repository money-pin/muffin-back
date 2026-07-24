package com.muffin.quiz.application.generation;

import lombok.Getter;

/** AI 일일 퀴즈 생성 결과가 서비스 저장 정책을 만족하지 못할 때 사용하는 예외. */
@Getter
public class DailyQuizGenerationException extends RuntimeException {

    private final DailyQuizGenerationFailureReason reason;

    public DailyQuizGenerationException(DailyQuizGenerationFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }
}
