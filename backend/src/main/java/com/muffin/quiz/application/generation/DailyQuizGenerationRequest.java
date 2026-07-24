package com.muffin.quiz.application.generation;

import java.time.LocalDate;
import java.util.List;

/** 일일 퀴즈 생성을 위해 AI에 전달할 하루치 뉴스 묶음. */
public record DailyQuizGenerationRequest(LocalDate quizDate, List<DailyQuizNewsSource> newsSources) {

    public DailyQuizGenerationRequest {
        newsSources = List.copyOf(newsSources);
    }
}
