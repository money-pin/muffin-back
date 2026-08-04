package com.muffin.mypage.domain.exception.code;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 마이페이지 조회 요청 값 검증/조회 에러 코드.
 */
@Getter
@AllArgsConstructor
public enum MypageErrorCode implements BaseErrorCode {
    INVALID_PAGE_REQUEST(HttpStatus.BAD_REQUEST, "MYPAGE_400_004", "페이지 요청 값이 올바르지 않습니다."),
    INVALID_YEAR_MONTH(HttpStatus.BAD_REQUEST, "MYPAGE_400_005", "month는 1~12 사이의 값이어야 합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "MYPAGE_404_001", "사용자 정보를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
