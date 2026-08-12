package com.muffin.sector.infrastructure.toss.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/** 토스증권 API 호출이 실패했을 때 발생한다. 원본 응답 본문이나 인증 정보는 담지 않고, 추적에 필요한 정보만 가진다. */
@Getter
public class TossApiException extends RuntimeException {

    private static final String INTERRUPTED_CODE = "INTERRUPTED";

    private final String requestId;
    private final String tossCode;
    private final HttpStatusCode httpStatus;

    public TossApiException(String requestId, String tossCode, HttpStatusCode httpStatus, String message) {
        super(message);
        this.requestId = requestId;
        this.tossCode = tossCode;
        this.httpStatus = httpStatus;
    }

    public TossApiException(
            String requestId, String tossCode, HttpStatusCode httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
        this.tossCode = tossCode;
        this.httpStatus = httpStatus;
    }

    public TossApiException(String message, Throwable cause) {
        super(message, cause);
        this.requestId = null;
        this.tossCode = null;
        this.httpStatus = null;
    }

    public static TossApiException interrupted(String message, InterruptedException cause) {
        return new TossApiException(null, INTERRUPTED_CODE, null, message, cause);
    }
}
