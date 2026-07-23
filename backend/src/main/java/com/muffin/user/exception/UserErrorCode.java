package com.muffin.user.exception;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_001", "존재하지 않는 사용자입니다."),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_002", "존재하지 않는 캐릭터입니다."),
    ONBOARDING_NOT_COMPLETED(HttpStatus.CONFLICT, "USER_409_001", "온보딩을 먼저 완료해야 합니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER_409_002", "이미 사용 중인 닉네임입니다."),
    NICKNAME_CONTAINS_PROFANITY(HttpStatus.BAD_REQUEST, "USER_400_001", "닉네임에 부적절한 표현이 포함되어 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
