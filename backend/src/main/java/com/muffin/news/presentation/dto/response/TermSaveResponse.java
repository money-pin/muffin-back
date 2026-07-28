package com.muffin.news.presentation.dto.response;

import java.time.LocalDateTime;

public record TermSaveResponse(Long termId, String term, boolean isSaved, LocalDateTime savedAt) {}
