package com.muffin.global.event;

import java.time.LocalDate;

/**
 * 정산 배치 한 번이 끝났음을 알리는 도메인 이벤트. 알림(NOTI-03)이 구독한다.
 *
 * <p>FAILED 건을 재처리하는 두 번째 실행도 같은 이벤트를 낸다. 구독 측은 그 시점의 SETTLED 건을 다시 읽고, 이미 보낸 사용자는 중복 방지 키로 거른다.
 *
 * @param settlementDate 정산 기준 일자(당일 시가 기준)
 * @param investDate 이번에 정산된 투자일(직전 거래일)
 */
public record SettlementCompletedEvent(LocalDate settlementDate, LocalDate investDate) {}
