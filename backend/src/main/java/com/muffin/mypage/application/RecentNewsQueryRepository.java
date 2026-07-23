package com.muffin.mypage.application;

import com.muffin.mypage.application.projection.RecentNewsProjection;
import java.util.List;

/** 최근 읽은 뉴스 목록 읽기 전용 포트. 대상 사용자의 열람 기록을 미삭제 뉴스와 조인해 열람 시각 내림차순 커서 페이지네이션으로 조회한다. */
public interface RecentNewsQueryRepository {

    /**
     * 사용자의 열람 기록을 열람 시각 내림차순으로 조회한다. 커서 이후의 행만 가져오며, hasNext 판정을 위해 보통 size+1을 넘긴다.
     *
     * @param cursor 최초 조회 시 {@code null}
     * @param limit 조회 개수
     */
    List<RecentNewsProjection> findRecentNewsPage(Long userId, RecentNewsCursor cursor, int limit);
}
