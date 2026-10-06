package com.muffin.briefing.application.exception;

/** 브리핑 생성 단계에서 AI 응답이 규격을 벗어났을 때 발생한다. 이 예외를 받은 브리핑은 이용 불가 상태로 저장된다. */
public class BriefingGenerationException extends RuntimeException {

    public BriefingGenerationException(String message) {
        super(message);
    }

    public BriefingGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
