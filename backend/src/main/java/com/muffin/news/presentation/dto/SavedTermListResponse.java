package com.muffin.news.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 저장한 용어 목록(페이지 기반) 응답. */
public record SavedTermListResponse(List<SavedTermItem> savedTerms, int page, int size, boolean hasNext) {

    public record SavedTermItem(Long termId, String term, String content, LocalDateTime savedAt) {}
}
