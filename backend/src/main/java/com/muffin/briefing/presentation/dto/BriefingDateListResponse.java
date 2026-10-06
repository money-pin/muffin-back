package com.muffin.briefing.presentation.dto;

import java.time.LocalDate;
import java.util.List;

/** 날짜 선택 칩용 최근 브리핑 목록. 최신순이다. */
public record BriefingDateListResponse(List<BriefingDateItem> items) {

    public record BriefingDateItem(LocalDate briefingDate, String headline) {}
}
