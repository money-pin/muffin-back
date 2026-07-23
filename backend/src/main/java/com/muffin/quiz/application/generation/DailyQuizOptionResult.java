package com.muffin.quiz.application.generation;

/** AI가 생성한 퀴즈 선택지 결과. */
public record DailyQuizOptionResult(int order, String text) {}
