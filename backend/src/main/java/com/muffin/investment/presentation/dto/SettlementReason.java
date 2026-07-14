package com.muffin.investment.presentation.dto;

/** 정산 결과가 없을 때의 사유. */
public enum SettlementReason {
    /** 최근 정산 대상 투자 자체가 없음(미투자/취소 포함). */
    NO_INVESTMENT,
    /** 정산 배치가 아직 완료되지 않음. */
    SETTLEMENT_PENDING
}
