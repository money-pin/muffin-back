package com.muffin.mypage.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 최근 읽은 뉴스 목록(커서 페이지네이션) 응답.
 *
 * @param nextCursor 다음 페이지 커서. 다음 페이지가 없으면 생략된다.
 */
public record RecentNewsResponse(
        List<RecentNewsItem> items, @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor, boolean hasNext) {

    /**
     * @param publishedAt 발행 시각(KST)
     * @param viewedAt 열람 시각(KST). read_history.readAt, 정렬/커서 기준
     */
    public record RecentNewsItem(
            Long newsId,
            String title,
            String categoryName,
            String thumbnailUrl,
            Long viewCount,
            OffsetDateTime publishedAt,
            OffsetDateTime viewedAt) {}
}
