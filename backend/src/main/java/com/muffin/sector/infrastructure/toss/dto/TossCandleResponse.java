package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * {@code GET /api/v1/candles} 응답(특정 종목의 특정 일자 일봉). 실제 토스증권 API 문서로 필드명과 형태를 재확인해야
 * 한다(§5.1).
 */
public record TossCandleResponse(
        @JsonProperty("base_date") LocalDate baseDate,
        @JsonProperty("open_price") Long openPrice,
        @JsonProperty("close_price") Long closePrice,
        @JsonProperty("high_price") Long highPrice,
        @JsonProperty("low_price") Long lowPrice) {}
