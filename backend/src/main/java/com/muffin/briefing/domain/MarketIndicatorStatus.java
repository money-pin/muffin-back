package com.muffin.briefing.domain;

/** 지표 수집 상태. 실제 값과 수집 실패를 0 같은 특수값 없이 구분한다. */
public enum MarketIndicatorStatus {
    /** 정상 수신. */
    SUCCESS,
    /** 호출은 성공했지만 해당 일자 데이터가 없음. 휴장일이 대표적이다. */
    NO_DATA,
    /** 호출, 파싱 또는 저장 실패. */
    FAILED
}
