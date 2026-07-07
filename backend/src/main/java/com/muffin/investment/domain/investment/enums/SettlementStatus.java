package com.muffin.investment.domain.investment.enums;

/** 정산 진행 상태. */
public enum SettlementStatus {
    /** 정산 대기(미완료). */
    PENDING,
    /** 정산 완료. */
    SETTLED,
    /** 정산 실패. */
    FAILED
}
