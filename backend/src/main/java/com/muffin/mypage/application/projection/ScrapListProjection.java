package com.muffin.mypage.application.projection;

import java.time.LocalDateTime;

/**
 * 스크랩 목록 조회 결과 한 행(읽기 전용 projection). scrap과 news를 조인해 뽑는다.
 *
 * @param scrappedAt 스크랩 시각(scrap.createdAt, SAVED_DESC 정렬/커서 기준)
 * @param scrapId 스크랩 id(SAVED_DESC 타이브레이크)
 */
public record ScrapListProjection(
        Long newsId,
        String title,
        String categoryName,
        String thumbnailUrl,
        Long viewCount,
        LocalDateTime publishedAt,
        LocalDateTime scrappedAt,
        Long scrapId) {}
