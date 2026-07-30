package com.muffin.news.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 뉴스 목록(커서 페이지네이션) 응답.
 *
 * <p>{@code thumbnailUrl}은 원본 썸네일이 있으면 그 URL, 없으면 null이다. 원본이 없을 때의 기본 이미지는 백엔드가
 * 관여하지 않고 프론트가 화면/카테고리에 맞춰 자체 에셋으로 렌더링한다.
 */
public record NewsListResponse(
        List<NewsListItem> items, @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor, boolean hasNext) {

    public record NewsListItem(
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
