package com.muffin.investment.domain.investment.enums;

/** 정산 진행 상태. */
public enum SettlementStatus {
    /** 정산 대기(미완료). */
    PENDING,
    /** 정산 완료. */
    SETTLED,
    /** 정산 실패. */
    FAILED,
    /** 정산 창(다음 거래일)을 놓쳐 취소된 확정 투자. 자산에는 영향을 주지 않는다. */
    CANCELLED,
    /** 투자하지 않은 날(NO_INVEST)이라 정산할 것이 없어 종료된 상태. */
    NO_SETTLEMENT
}
