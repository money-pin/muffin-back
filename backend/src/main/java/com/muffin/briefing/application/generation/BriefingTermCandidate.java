package com.muffin.briefing.application.generation;

/** 오늘의 용어 후보. 브리핑에 등장한 뉴스에 연결된 용어 중에서 고른다. */
public record BriefingTermCandidate(Long termId, String term, String content) {}
