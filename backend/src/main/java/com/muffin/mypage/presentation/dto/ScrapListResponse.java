package com.muffin.mypage.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 스크랩한 뉴스 목록(커서 페이지네이션) 응답.
 *
 * @param nextCursor 다음 페이지 커서. 다음 페이지가 없으면(hasNext=false) null이다.
 */
public record ScrapListResponse(List<ScrapItem> items, String nextCursor, boolean hasNext) {

    /**
     * @param publishedAt 발행 시각(KST). PUBLISHED_DESC 정렬 기준
     * @param scrappedAt 스크랩 시각(KST). SAVED_DESC 정렬 기준
     */
    public record ScrapItem(
            Long newsId,
            String title,
            String categoryName,
            String thumbnailUrl,
            Long viewCount,
            OffsetDateTime publishedAt,
            OffsetDateTime scrappedAt) {}
}
