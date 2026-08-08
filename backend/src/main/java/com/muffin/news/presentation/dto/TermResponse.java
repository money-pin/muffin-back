package com.muffin.news.presentation.dto;

public record TermResponse(Long termId, String term, String content, boolean isSaved) {}
