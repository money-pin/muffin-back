package com.muffin.news.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 오늘의 뉴스(당일 수집 공개 뉴스 최대 3건) 응답. thumbnailUrl은 대체 정책까지 적용된 최종 값이다. */
public record NewsTodayResponse(List<NewsTodayItem> items) {

    public record NewsTodayItem(
            Long newsId,
            Long categoryId,
            String categoryName,
            String title,
            String summary,
            String publisher,
            LocalDateTime publishedAt,
            String thumbnailUrl,
            Long viewCount) {}
}
