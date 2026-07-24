package com.muffin.news.application.term;

import java.time.LocalDateTime;

/** 저장한 용어 목록 조회 결과 한 행(용어 사전 정보 + 저장 시각). */
public record SavedTermRow(Long termId, String term, String content, LocalDateTime savedAt) {}
