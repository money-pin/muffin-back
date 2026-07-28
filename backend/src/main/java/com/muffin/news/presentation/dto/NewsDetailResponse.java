package com.muffin.news.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 뉴스 상세 응답. AI 재구성 본문을 세그먼트 배열로 제공한다.
 *
 * <p>{@code thumbnailUrl}은 원본 썸네일이 있으면 그 URL, 없으면 null이다. 원본이 없을 때의 기본 이미지는 백엔드가
 * 관여하지 않고 프론트가 화면/카테고리에 맞춰 자체 에셋으로 렌더링한다.
 */
public record NewsDetailResponse(
        Long newsId,
        String title,
        String categoryName,
        Long viewCount,
        String publisher,
        LocalDateTime publishedAt,
        String thumbnailUrl,
        String originalUrl,
        List<BodySegment> bodySegments,
        boolean isScrapped) {

    /** 본문 세그먼트. TEXT는 일반 텍스트, HIGHLIGHT는 용어 사전과 연결되는 강조 구간이다(termId 포함). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BodySegment(String type, String text, Long termId) {

        /** 하이라이트 없이 전체를 일반 텍스트로 제공하는 세그먼트를 만든다. */
        public static BodySegment text(String text) {
            return new BodySegment("TEXT", text, null);
        }

        /** 용어 사전과 연결되는 하이라이트 세그먼트를 만든다. */
        public static BodySegment highlight(String text, Long termId) {
            return new BodySegment("HIGHLIGHT", text, termId);
        }
    }
}
