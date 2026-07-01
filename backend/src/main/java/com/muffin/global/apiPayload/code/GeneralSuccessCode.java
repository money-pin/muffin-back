package com.muffin.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode {
    OK(HttpStatus.OK, "COMMON_200_001", "성공적으로 요청을 처리했습니다."),
    CREATED(HttpStatus.CREATED, "COMMON_201_001", "리소스가 성공적으로 생성되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
