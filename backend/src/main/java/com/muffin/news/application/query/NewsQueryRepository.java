package com.muffin.news.application.query;

import com.muffin.news.application.sectorimpact.SectorRow;
import java.time.LocalDateTime;
import java.util.List;

/** 뉴스 조회 읽기 전용 인터페이스. 공개(PUBLISHED)·미삭제 뉴스만 대상으로 한다. */
public interface NewsQueryRepository {

    /**
     * 커서 이후의 공개 뉴스를 {@code publishedAt DESC, newsId DESC}로 조회한다.
     *
     * @param cursor 최초 조회 시 {@code null}
     * @param categoryId {@code null}이면 전체 카테고리
     * @param limit 조회 개수(hasNext 판정을 위해 보통 size+1을 넘긴다)
     */
    List<NewsSummaryRow> findPublishedNewsPage(NewsCursor cursor, Long categoryId, int limit);

    /** 지정 구간에 수집(createdAt)된 공개 뉴스를 최신 발행순으로 조회한다(오늘의 뉴스). */
    List<NewsSummaryRow> findTodayPublishedNews(LocalDateTime startInclusive, LocalDateTime endExclusive, int limit);

    /** 활성 섹터를 그룹·섹터 표시 순서(groupOrder, sectorOrder)대로 조회한다(섹터 영향도 응답의 고정 목록). */
    List<SectorRow> findActiveSectorsInDisplayOrder();

    /** 마이페이지 홈에 표시할 사용자의 최근 열람 뉴스를 열람 시각 최신순으로 조회한다. */
    List<RecentReadNewsRow> findRecentReadNews(Long userId, int limit);
}
