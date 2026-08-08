package com.muffin.news.presentation.dto;

import java.time.LocalDateTime;

public record TermSaveResponse(Long termId, String term, boolean isSaved, LocalDateTime savedAt) {}
