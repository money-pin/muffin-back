package com.muffin.news.application.query;

import java.time.LocalDateTime;

/**
 * 마이페이지 홈에 표시할 최근 열람 뉴스 조회용 읽기 전용 프로젝션.
 *
 * <p>홈과 최근 읽은 뉴스 목록이 같은 뉴스 카드를 그리므로 카드가 쓰는 값을 모두 담는다. 한쪽만 빠지면 그 화면에서 조회수와 날짜가
 * 빈 값으로 렌더링된다.
 *
 * @param viewedAt 열람 시각(read_history.readAt, 정렬 기준)
 */
public record RecentReadNewsRow(
        Long newsId,
        String title,
        String categoryName,
        String thumbnailUrl,
        Long viewCount,
        LocalDateTime publishedAt,
        LocalDateTime viewedAt,
        boolean isScrapped) {}
