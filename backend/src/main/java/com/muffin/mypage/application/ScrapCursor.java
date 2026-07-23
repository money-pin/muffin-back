package com.muffin.mypage.application;

import com.muffin.mypage.domain.ScrapSort;
import java.time.LocalDateTime;

/**
 * 스크랩 목록 페이지네이션 커서. 정렬마다 기준 키가 달라, 정렬 종류와 기준 키(시각 또는 조회수) + 타이브레이크 id를 함께 담는다.
 *
 * <p>{@link ScrapSort#SAVED_DESC}/{@link ScrapSort#PUBLISHED_DESC}는 {@code timeKey}(저장/발행 시각)를,
 * {@link ScrapSort#VIEW_DESC}는 {@code numberKey}(조회수)를 사용한다. 나머지는 {@code null}이다.
 */
public record ScrapCursor(ScrapSort sort, LocalDateTime timeKey, Long numberKey, long id) {}
