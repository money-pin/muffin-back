package com.muffin.briefing.presentation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 브리핑 열람 기록 결과. */
public record BriefingViewResponse(LocalDate briefingDate, LocalDateTime viewedAt) {}
