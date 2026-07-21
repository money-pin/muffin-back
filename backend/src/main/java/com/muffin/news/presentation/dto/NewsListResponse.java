package com.muffin.news.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/** 뉴스 목록(커서 페이지네이션) 응답. */
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
            Long viewCount) {}
}
