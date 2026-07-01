package com.muffin.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400_001", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_401_001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_403_001", "요청이 거부되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404_001", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500_001", "예기치 않은 서버 에러가 발생했습니다."),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "COMMON_400_002", "요청 값 검증에 실패했습니다."),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405_001", "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_415_001", "지원하지 않는 Content-Type입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
