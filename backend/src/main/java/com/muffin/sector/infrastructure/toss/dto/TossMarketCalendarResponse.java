package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * {@code GET /api/v1/market-calendar/KR} 응답. 실제 데이터는 {@code result} 안에 감싸여 있다(공통 BFF envelope).
 *
 * <p>{@code integrated}가 {@code null}이면 그날은 전 세션이 휴장이다(공휴일 등). {@code regularMarket}의 존재
 * 여부로 거래일인지 판정할 수 있다. 거래일 판정 정책 자체는 이 클래스가 아니라 별도 서비스의 책임이다(§5.2, 이번 슬라이스
 * 범위 밖).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossMarketCalendarResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(BusinessDay today, BusinessDay previousBusinessDay, BusinessDay nextBusinessDay) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BusinessDay(LocalDate date, Sessions integrated) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sessions(Session preMarket, Session regularMarket, Session afterMarket) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(String startTime, String endTime) {}
}
