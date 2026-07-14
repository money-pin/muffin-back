package com.muffin.sector.domain.etfprice;

/** ETF 일별 시가·종가의 수집 상태. 실제 가격과 수집 실패를 0 같은 특수값 없이 구분한다. */
public enum PriceCollectionStatus {
    /** 아직 해당 가격 수집을 시도하지 않음. */
    PENDING,
    /** 정상 가격 수신 및 저장 완료. */
    SUCCESS,
    /** API 호출은 성공했지만 해당 날짜의 캔들이 없음. 재시도 대상. */
    NO_DATA,
    /** API 호출, 가격 파싱 또는 저장 실패. 재시도 대상. */
    FAILED,
    /** 정산 마감 시각까지 시가를 확보하지 못해 0% 폴백 대상으로 확정됨. */
    FINAL_MISSING,
    /** 국내 시장 휴장으로 가격 수집 대상이 아님. */
    MARKET_CLOSED
}
