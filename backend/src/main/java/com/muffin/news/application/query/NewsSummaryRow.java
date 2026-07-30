package com.muffin.news.application.query;

import java.time.LocalDateTime;

/** 뉴스 목록/오늘의 뉴스 조회용 읽기 전용 프로젝션. */
public record NewsSummaryRow(
        Long newsId,
        Long categoryId,
        String categoryName,
        String title,
        String summary,
        String publisher,
        LocalDateTime publishedAt,
        String thumbnailUrl,
        Long viewCount,
        boolean isScrapped) {}
