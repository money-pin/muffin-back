package com.muffin.mypage.application;

import com.muffin.mypage.application.projection.ScrapListProjection;
import com.muffin.mypage.domain.ScrapSort;
import java.util.List;

/** 스크랩 목록 읽기 전용 포트. 대상 사용자의 스크랩을 미삭제 뉴스와 조인해 정렬/커서 페이지네이션으로 조회한다. */
public interface ScrapListQueryRepository {

    /**
     * 사용자의 스크랩을 정렬 기준으로 조회한다. 커서 이후의 행만 가져오며, hasNext 판정을 위해 보통 size+1을 넘긴다.
     *
     * @param cursor 최초 조회 시 {@code null}
     * @param limit 조회 개수
     */
    List<ScrapListProjection> findScrapPage(Long userId, ScrapSort sort, ScrapCursor cursor, int limit);
}
