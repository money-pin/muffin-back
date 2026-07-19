package com.muffin.news.application.explanation;

/** AI가 생성한 해설카드 한 장의 결과. */
public record NewsExplanationCardResult(int order, String title, String body, String keyTerm) {}
