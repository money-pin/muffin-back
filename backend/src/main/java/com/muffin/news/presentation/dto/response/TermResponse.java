package com.muffin.news.presentation.dto.response;

public record TermResponse(Long termId, String term, String content, boolean isSaved) {}
