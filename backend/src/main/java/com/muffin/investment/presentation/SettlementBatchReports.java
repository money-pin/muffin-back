package com.muffin.investment.presentation;

import com.muffin.global.batch.BatchJobReport;
import com.muffin.investment.application.settlement.SettlementBatchResult;

/**
 * 정산 배치 결과를 실행 로그 보고서로 옮긴다.
 *
 * <p>정산은 스케줄러(2차)와 이벤트 리스너(1차) 두 경로로 들어오는데, 같은 잡이므로 로그에 실리는 수치도 같아야 한다. 매핑을 한 곳에 두어 두 경로가 갈라지지 않게 한다.
 *
 * <p><b>일부 실패는 실패로 올리지 않는다.</b> 52건 중 1건이 실패해도 배치 자체는 정상 수행됐고, 그 1건은 {@code settlement_status=FAILED}로 남아
 * 다음 실행에서 재처리된다. 이걸 {@code outcome=failure}로 올리면 마지막 성공 시각이 갱신되지 않아 <b>"배치가 아예 안 돌았다"와 "1건이 재시도 대기 중"이 같은
 * 신호가 된다.</b> 건별 실패는 {@code failed=} 수치와 도메인 테이블이 답하고, 별도 임계값으로 알린다.
 *
 * <p>다만 <b>전량 실패는 실패로 올린다.</b> 대상이 있는데 성공이 하나도 없으면 개별 건의 문제가 아니라 배치가 실패한 것이다.
 */
final class SettlementBatchReports {

    private SettlementBatchReports() {}

    static BatchJobReport from(SettlementBatchResult result) {
        if (!result.ready()) {
            // 휴장과 시가 미적재는 진입 게이트에서 함께 걸리므로 하나의 이유로 남긴다.
            return BatchJobReport.skipped("no_trading_signal");
        }
        BatchJobReport report =
                isTotalFailure(result) ? BatchJobReport.failure("all_targets_failed") : BatchJobReport.success();
        return report.with("total", result.total())
                .with("success", result.success())
                .with("failed", result.failed());
    }

    private static boolean isTotalFailure(SettlementBatchResult result) {
        return result.total() > 0 && result.success() == 0;
    }
}
