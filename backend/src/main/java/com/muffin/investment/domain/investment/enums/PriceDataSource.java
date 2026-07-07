package com.muffin.investment.domain.investment.enums;

/** 섹터 정산 시 사용한 가격 데이터 출처. */
public enum PriceDataSource {
    /** 정상 ETF 시가 사용. */
    NORMAL,
    /** 거래 정지 등으로 0% 적용(Fallback). */
    FALLBACK_ZERO
}
