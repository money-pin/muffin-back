package com.muffin.news.application.explanation;

/** 해설카드 생성 후보로 OpenAI에 전달할 용어 정보. */
public record NewsExplanationTermCandidate(Long termId, String term) {}
