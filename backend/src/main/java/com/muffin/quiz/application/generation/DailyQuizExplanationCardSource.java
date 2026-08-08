package com.muffin.quiz.application.generation;

/** 일일 퀴즈 생성에 참고할 뉴스 해설카드 정보 */
public record DailyQuizExplanationCardSource(int order, String title, String keyTerm, String content) {}
