package com.muffin.global.event;

import java.time.LocalDate;

/**
 * 주간 랭킹이 확정되었음을 알리는 도메인 이벤트. 알림(NOTI-04)이 구독한다.
 *
 * @param weekStartDate 확정된 주의 월요일
 */
public record WeeklyRankingCreatedEvent(LocalDate weekStartDate) {}
