package com.muffin.mypage.application;

import java.time.LocalDateTime;

/**
 * 최근 읽은 뉴스 목록 페이지네이션 커서. 정렬이 열람 시각(read_at) 내림차순 하나뿐이라, 기준 키(열람 시각)와 동률을 끊는 타이브레이크 id만 담는다.
 *
 * @param viewedAt 열람 시각(read_history.readAt)
 * @param id 열람 기록 id(read_history.id, 타이브레이크)
 */
public record RecentNewsCursor(LocalDateTime viewedAt, long id) {}
