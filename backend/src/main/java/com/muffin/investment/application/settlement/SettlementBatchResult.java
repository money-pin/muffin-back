package com.muffin.investment.application.settlement;

import java.time.LocalDate;

/**
 * 정산 배치 1회 실행 결과 요약. {@code SettlementBatchReports}가 이 값을 배치 실행 로그 한 줄의 수치로 옮기고, 이후 배치 메트릭의 태그/카운터가 된다.
 *
 * <p>한때 batch_log 테이블에 영속화할 값으로 만들었으나 테이블은 미도입으로 확정됐다(건별 성공/실패는 {@code investment.settlement_status}가 이미
 * 들고 있고, 테이블은 알림을 쏘지 못한다). 지금의 소비처는 로그와 메트릭이다.
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
