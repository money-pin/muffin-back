package com.muffin.sector.infrastructure.coingecko.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/** CoinGecko API 호출이 실패했을 때 발생한다. 원본 응답 본문이나 인증 정보는 담지 않고, 추적에 필요한 정보만 가진다. */
@Getter
public class CoinGeckoApiException extends RuntimeException {

    private final HttpStatusCode httpStatus;

    public CoinGeckoApiException(HttpStatusCode httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public CoinGeckoApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
    }
}
