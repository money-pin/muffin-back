package com.muffin.quiz.application.generation;

/** 일일 퀴즈 생성을 위해 AI에 전달할 재구성 완료 뉴스. */
public record DailyQuizNewsSource(Long newsId, String title, String rewrittenBody) {}
