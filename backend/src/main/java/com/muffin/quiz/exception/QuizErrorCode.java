package com.muffin.quiz.exception;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum QuizErrorCode implements BaseErrorCode {
    QUIZ_UNAVAILABLE(HttpStatus.NOT_FOUND, "QUIZ_404_001", "오늘의 퀴즈를 아직 준비 중입니다."),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "QUIZ_404_002", "존재하지 않는 퀴즈 문항입니다."),
    QUIZ_OPTION_NOT_FOUND(HttpStatus.BAD_REQUEST, "QUIZ_400_001", "해당 퀴즈 문항의 선택지가 아닙니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
