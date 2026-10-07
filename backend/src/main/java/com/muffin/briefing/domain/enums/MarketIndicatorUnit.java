package com.muffin.briefing.domain.enums;

/** 지표 값의 단위. 화면이 숫자를 어떻게 포매팅할지 판단하는 데 쓴다. */
public enum MarketIndicatorUnit {
    /** 지수 포인트. 예: 코스피 2,812.45 */
    POINT,
    /** 원. 예: 원/달러 1,380.5 */
    KRW,
    /** 달러. ETF 가격이라 지수 레벨과 다르다. 예: QQQ 512.30 */
    USD
}
