package com.muffin.investment.application;

import java.time.LocalDate;

/**
 * 정산 배치 1회 실행 결과 요약. 추후 batch_log 영속화 시 이 값을 그대로 기록한다.
 *
 * @param settlementDate 정산 기준 일자(당일 시가 기준)
 * @param ready ETF 시세 적재 완료로 실제 정산을 수행했는지(false면 스킵)
 * @param total 처리 대상 건수
 * @param success 정산 성공 건수
 * @param failed 정산 실패(FAILED) 건수
 */
public record SettlementBatchResult(LocalDate settlementDate, boolean ready, int total, int success, int failed) {

    /** ETF 시세 미적재로 정산을 건너뛴 결과. */
    public static SettlementBatchResult skipped(LocalDate settlementDate) {
        return new SettlementBatchResult(settlementDate, false, 0, 0, 0);
    }
}
