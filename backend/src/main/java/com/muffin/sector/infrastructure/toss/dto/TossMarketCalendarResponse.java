package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * {@code GET /api/v1/market-calendar/KR} 응답(특정 일자의 거래일 여부). 실제 토스증권 API 문서로 필드명과 형태를
 * 재확인해야 한다(§5.1). 거래일 판단 정책(§5.2) 자체는 이 클라이언트가 아니라 별도 서비스의 책임이다.
 */
public record TossMarketCalendarResponse(
        @JsonProperty("date") LocalDate date, @JsonProperty("is_trading_day") boolean tradingDay) {}
