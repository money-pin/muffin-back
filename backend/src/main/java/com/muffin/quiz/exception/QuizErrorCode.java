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
    QUIZ_OPTION_NOT_FOUND(HttpStatus.BAD_REQUEST, "QUIZ_400_001", "해당 퀴즈 문항의 선택지가 아닙니다."),
    QUIZ_HISTORY_FUTURE_DATE(HttpStatus.BAD_REQUEST, "QUIZ_400_002", "미래 날짜의 퀴즈 기록은 조회할 수 없습니다."),
    QUIZ_HISTORY_INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "QUIZ_400_003", "날짜는 yyyy-MM-dd 형식으로 입력해 주세요."),
    QUIZ_RESULT_NOT_READY(HttpStatus.CONFLICT, "QUIZ_409_001", "퀴즈를 모두 완료한 후 결과를 조회할 수 있습니다.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
