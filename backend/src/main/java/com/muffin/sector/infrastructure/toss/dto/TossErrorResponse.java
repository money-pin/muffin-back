package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 토스증권 API 에러 응답. 실제 에러 정보는 {@code error} 필드 안에 중첩되어 있다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(TossError error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossError(String requestId, String code, String message, Object data) {}
}
