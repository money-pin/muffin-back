package com.muffin.mypage.domain.exception;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;

/** 마이페이지 조회 요청 검증/조회 오류를 전역 예외 처리기의 표준 응답으로 변환하기 위한 전용 예외. */
public class MypageException extends GeneralException {

    public MypageException(MypageErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
