package com.muffin.global.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchJobReportTest {

    @Test
    @DisplayName("추가한 수치는 로그 필드 순서가 실행마다 흔들리지 않도록 삽입 순서를 유지한다")
    void with_keepsInsertionOrder() {
        BatchJobReport report =
                BatchJobReport.success().with("total", 10).with("success", 9).with("failed", 1);

        assertThat(report.details().keySet()).containsExactly("total", "success", "failed");
    }

    @Test
    @DisplayName("수치를 추가해도 원본 보고서는 바뀌지 않는다")
    void with_returnsNewReport() {
        BatchJobReport original = BatchJobReport.success();

        BatchJobReport extended = original.with("collected", 3);

        assertThat(original.details()).isEmpty();
        assertThat(extended.details()).containsEntry("collected", 3);
    }

    @Test
    @DisplayName("보고서의 수치는 밖에서 바꿀 수 없다")
    void details_areUnmodifiable() {
        BatchJobReport report = BatchJobReport.success().with("total", 1);

        assertThatThrownBy(() -> report.details().put("total", 2)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("건너뛴 보고서는 이유를 갖고 결과가 SKIPPED다")
    void skipped_carriesReason() {
        BatchJobReport report = BatchJobReport.skipped("market_closed");

        assertThat(report.outcome()).isEqualTo(BatchOutcome.SKIPPED);
        assertThat(report.reason()).isEqualTo("market_closed");
    }
}
