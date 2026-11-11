package com.muffin.global.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 모닝 브리핑이 사용자에게 공개되었음을 알리는 도메인 이벤트. 알림(NOTI-01)이 구독한다.
 *
 * <p>누락 복구 때문에 지난 날짜 브리핑이 함께 공개될 수 있으므로, 구독 측이 오늘 브리핑인지 판단할 수 있게 기준일과 공개 시각을 함께 싣는다.
 *
 * @param briefingDate 브리핑 기준일
 * @param publishedAt 공개 시각
 */
public record BriefingPublishedEvent(LocalDate briefingDate, LocalDateTime publishedAt) {}
