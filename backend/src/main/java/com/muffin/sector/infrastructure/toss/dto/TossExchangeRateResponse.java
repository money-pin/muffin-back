package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code GET /api/v1/exchange-rate} 응답. 다른 응답과 같이 {@code result}로 감싸여 있다.
 *
 * <p>가격 필드는 {@link TossCandleResponse}와 같은 이유로 {@link String}으로 받는다. 공식 문서의 모델 정의와 예시
 * 응답의 와이어 타입이 서로 달라, 호출부에서 명시적으로 파싱하는 편이 안전하다.
 *
 * <p>1분 주기로 갱신되는 참고용 표시 환율이며 실제 거래 환율과 다를 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossExchangeRateResponse(Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String baseCurrency,
            String quoteCurrency,
            String rate,
            String midRate,
            String basisPoint,
            String rateChangeType,
            String validFrom,
            String validUntil) {}
}
