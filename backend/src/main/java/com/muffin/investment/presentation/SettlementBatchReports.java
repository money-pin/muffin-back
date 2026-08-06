package com.muffin.investment.presentation;

import com.muffin.global.batch.BatchJobReport;
import com.muffin.investment.application.settlement.SettlementBatchResult;

/**
 * 정산 배치 결과를 실행 로그 보고서로 옮긴다.
 *
 * <p>정산은 스케줄러(2차)와 이벤트 리스너(1차) 두 경로로 들어오는데, 같은 잡이므로 로그에 실리는 수치도 같아야 한다. 매핑을 한 곳에 두어 두 경로가 갈라지지 않게 한다.
 */
final class SettlementBatchReports {

    private SettlementBatchReports() {}

    static BatchJobReport from(SettlementBatchResult result) {
        if (!result.ready()) {
            // 휴장과 시가 미적재는 진입 게이트에서 함께 걸리므로 하나의 이유로 남긴다.
            return BatchJobReport.skipped("no_trading_signal");
        }
        return BatchJobReport.success()
                .with("total", result.total())
                .with("success", result.success())
                .with("failed", result.failed());
    }
}
