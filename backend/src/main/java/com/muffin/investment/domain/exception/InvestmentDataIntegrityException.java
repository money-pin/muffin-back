package com.muffin.investment.domain.exception;

/** 투자 데이터가 참조하는 기준정보가 누락된 서버 내부 정합성 오류. */
public class InvestmentDataIntegrityException extends RuntimeException {

    public InvestmentDataIntegrityException(Long sectorId) {
        super("투자 섹터 기준 정보를 찾을 수 없습니다: sectorId=" + sectorId);
    }
}
