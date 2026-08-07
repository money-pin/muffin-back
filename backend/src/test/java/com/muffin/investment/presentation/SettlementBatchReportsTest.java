package com.muffin.investment.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchOutcome;
import com.muffin.investment.application.settlement.SettlementBatchResult;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementBatchReportsTest {

    private static final LocalDate SETTLE_DATE = LocalDate.of(2026, 8, 6);

    @Test
    @DisplayName("일부만 실패하면 배치는 성공이고 실패 건수는 수치로 남는다")
    void from_keepsPartialFailureAsSuccess() {
        BatchJobReport report = SettlementBatchReports.from(new SettlementBatchResult(SETTLE_DATE, true, 52, 51, 1));

        assertThat(report.outcome()).isEqualTo(BatchOutcome.SUCCESS);
        assertThat(report.details()).containsEntry("total", 52).containsEntry("failed", 1);
    }

    @Test
    @DisplayName("대상이 있는데 성공이 하나도 없으면 배치 실패로 올린다")
    void from_reportsFailureWhenEveryTargetFailed() {
        BatchJobReport report = SettlementBatchReports.from(new SettlementBatchResult(SETTLE_DATE, true, 52, 0, 52));

        assertThat(report.outcome()).isEqualTo(BatchOutcome.FAILURE);
        assertThat(report.reason()).isEqualTo("all_targets_failed");
        assertThat(report.details()).containsEntry("failed", 52);
    }

    @Test
    @DisplayName("정산 대상이 아예 없는 것은 실패가 아니다")
    void from_treatsEmptyTargetsAsSuccess() {
        BatchJobReport report = SettlementBatchReports.from(new SettlementBatchResult(SETTLE_DATE, true, 0, 0, 0));

        assertThat(report.outcome()).isEqualTo(BatchOutcome.SUCCESS);
    }

    @Test
    @DisplayName("휴장이나 시가 미적재로 진입하지 못하면 건너뛴 것으로 남긴다")
    void from_reportsSkipWhenNotReady() {
        BatchJobReport report = SettlementBatchReports.from(SettlementBatchResult.skipped(SETTLE_DATE));

        assertThat(report.outcome()).isEqualTo(BatchOutcome.SKIPPED);
        assertThat(report.reason()).isEqualTo("no_trading_signal");
    }
}
