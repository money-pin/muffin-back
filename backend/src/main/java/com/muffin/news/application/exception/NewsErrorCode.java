package com.muffin.news.application.exception;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 뉴스 선별 및 재구성 파이프라인에서 사용하는 에러 코드를 정의한다. */
@Getter
@AllArgsConstructor
public enum NewsErrorCode implements BaseErrorCode {
    NEWS_NOT_PUBLISHED(HttpStatus.FORBIDDEN, "CONTENT_403_001", "아직 공개되지 않은 뉴스입니다."),
    NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_404_001", "존재하지 않는 뉴스입니다."),
    NEWS_TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_404_002", "존재하지 않는 용어입니다."),

    NEWS_SELECTION_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CONTENT_502_001", "뉴스 선별 AI 요청에 실패했습니다."),
    NEWS_SELECTION_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "CONTENT_502_002", "뉴스 선별 AI 응답 형식이 올바르지 않습니다."),
    ARTICLE_CONTENT_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "CONTENT_502_003", "뉴스 원문을 불러오지 못했습니다."),
    NEWS_RECONSTRUCTION_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "CONTENT_502_004", "뉴스 재구성 AI 요청에 실패했습니다."),
    NEWS_RECONSTRUCTION_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "CONTENT_502_005", "뉴스 재구성 AI 응답 형식이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
