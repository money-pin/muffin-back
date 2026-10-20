package com.muffin.auth.infrastructure.apple;

/**
 * client_secret을 만들 수 없는 서버 설정·서명 오류. IllegalStateException은 전역 처리기가 409로 바꾸고 메시지를 응답에
 * 넣으므로 쓰지 않는다. 이 예외는 500(응답에 상세 없음) + 에러 로그로 처리된다.
 */
public class AppleClientSecretException extends RuntimeException {

    public AppleClientSecretException(String message) {
        super(message);
    }

    public AppleClientSecretException(String message, Throwable cause) {
        super(message, cause);
    }
}
