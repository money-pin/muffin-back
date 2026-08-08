package com.muffin.news.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 오늘의 뉴스(경제·증권·세계 카테고리별 당일 최신 공개 뉴스) 응답.
 *
 * <p>{@code thumbnailUrl}은 원본 썸네일이 있으면 그 URL, 없으면 null이다. 원본이 없을 때의 기본 이미지는 백엔드가
 * 관여하지 않고 프론트가 자체 에셋으로 렌더링한다.
 */
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
            Long viewCount,
            boolean isScrapped) {}
}
