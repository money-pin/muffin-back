package com.muffin.mypage.application.projection;

import java.time.LocalDateTime;

/**
 * 최근 읽은 뉴스 목록 조회 결과 한 행(읽기 전용 projection). read_history와 news를 조인해 뽑는다.
 *
 * @param viewedAt 열람 시각(read_history.readAt, 정렬/커서 기준)
 * @param isScrapped 현재 사용자의 스크랩 여부
 * @param readHistoryId 열람 기록 id(타이브레이크)
 */
public record RecentNewsProjection(
        Long newsId,
        String title,
        String categoryName,
        String thumbnailUrl,
        Long viewCount,
        LocalDateTime publishedAt,
        LocalDateTime viewedAt,
        boolean isScrapped,
        Long readHistoryId) {}
