package com.muffin.notification.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.batch.BatchOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDispatchSummaryTest {

    @Test
    @DisplayName("일부 기기 실패는 늘 생기므로 한 건이라도 전달했으면 성공으로 보고한다")
    void partialFailureIsSuccess() {
        NotificationDispatchSummary summary =
                NotificationDispatchSummary.empty().add(DispatchOutcome.SENT).add(DispatchOutcome.FAILED);

        assertThat(summary.toReport().outcome()).isEqualTo(BatchOutcome.SUCCESS);
        assertThat(summary.toReport().details()).containsEntry("sent", 1).containsEntry("failed", 1);
    }

    @Test
    @DisplayName("한 건도 전달하지 못했으면 발송 경로가 막힌 것으로 보고 실패로 보고한다")
    void allFailedIsFailure() {
        NotificationDispatchSummary summary =
                NotificationDispatchSummary.empty().add(DispatchOutcome.FAILED);

        assertThat(summary.toReport().outcome()).isEqualTo(BatchOutcome.FAILURE);
    }

    @Test
    @DisplayName("모두 이미 보낸 재실행은 성공이다")
    void allDuplicateIsSuccess() {
        NotificationDispatchSummary summary =
                NotificationDispatchSummary.empty().add(DispatchOutcome.DUPLICATE);

        assertThat(summary.toReport().outcome()).isEqualTo(BatchOutcome.SUCCESS);
    }

    @Test
    @DisplayName("보내지 않고 끝난 실행은 그 사유로 건너뜀을 보고한다")
    void skipped() {
        assertThat(NotificationDispatchSummary.skipped("outside_window")
                        .toReport()
                        .reason())
                .isEqualTo("outside_window");
    }
}
