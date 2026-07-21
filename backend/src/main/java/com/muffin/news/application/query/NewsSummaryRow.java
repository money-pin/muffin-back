package com.muffin.news.application.query;

import java.time.LocalDateTime;

/**
 * 뉴스 목록/오늘의 뉴스 조회용 읽기 전용 프로젝션.
 *
 * <p>{@code categoryFallbackThumbnailUrl}은 오늘의 뉴스 썸네일 대체 정책에만 쓰이며, 목록 응답에서는 사용하지 않는다.
 */
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
        String categoryFallbackThumbnailUrl) {}
