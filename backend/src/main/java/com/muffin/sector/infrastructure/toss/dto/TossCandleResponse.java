package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * {@code GET /api/v1/candles} 응답. 실제 데이터는 {@code result} 안에 감싸여 있다(공통 BFF envelope).
 *
 * <p>{@code timestamp}/가격 필드는 공식 문서 모델 정의(Date/BigDecimal)와 예시 응답(따옴표로 감싼 문자열)이 서로 달라
 * 실제 와이어 타입이 불확실하다. {@link String}으로 받아 호출부에서 명시적으로 파싱하면 숫자·문자열 어느 쪽으로 오더라도
 * 안전하게 처리할 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossCandleResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(List<Candle> candles, String nextBefore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candle(
            String timestamp,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String volume,
            String currency) {}
}
