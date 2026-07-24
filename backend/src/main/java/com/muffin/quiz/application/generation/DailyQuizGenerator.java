package com.muffin.quiz.application.generation;

/** 재구성 완료 뉴스를 바탕으로 하루치 퀴즈 3문항을 생성한다. */
public interface DailyQuizGenerator {

    DailyQuizGenerationResult generate(DailyQuizGenerationRequest request);
}
