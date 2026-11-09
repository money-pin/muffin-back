package com.muffin.notification.application.dispatch;

import com.muffin.global.batch.BatchJobReport;

/**
 * 알림 한 번의 발송 실행 결과. 배치 실행 로그 한 줄의 수치가 된다.
 *
 * @param skipReason 보내지 않고 끝난 이유. 보낸 실행이면 {@code null}
 */
public record NotificationDispatchSummary(String skipReason, int sent, int duplicate, int failed, int noDevice) {

    public static NotificationDispatchSummary skipped(String reason) {
        return new NotificationDispatchSummary(reason, 0, 0, 0, 0);
    }

    public static NotificationDispatchSummary empty() {
        return new NotificationDispatchSummary(null, 0, 0, 0, 0);
    }

    public NotificationDispatchSummary add(DispatchOutcome outcome) {
        return switch (outcome) {
            case SENT -> new NotificationDispatchSummary(skipReason, sent + 1, duplicate, failed, noDevice);
            case DUPLICATE -> new NotificationDispatchSummary(skipReason, sent, duplicate + 1, failed, noDevice);
            case FAILED -> new NotificationDispatchSummary(skipReason, sent, duplicate, failed + 1, noDevice);
            case NO_DEVICE -> new NotificationDispatchSummary(skipReason, sent, duplicate, failed, noDevice + 1);
        };
    }

    /**
     * 기기 단위 실패는 앱 삭제·토큰 만료로 늘 일정량 생기므로 잡 실패로 보지 않는다. 한 건도 전달하지 못했을 때만 실패로 보고해 발송 경로 전체가 막힌 상황을
     * 알림 룰이 잡게 한다. 기기가 없는 사용자(noDevice)는 발송 경로와 무관하므로 이 판단에 넣지 않는다.
     */
    public BatchJobReport toReport() {
        if (skipReason != null) {
            return BatchJobReport.skipped(skipReason);
        }
        BatchJobReport report = (failed > 0 && sent == 0 && duplicate == 0)
                ? BatchJobReport.failure("all_deliveries_failed")
                : BatchJobReport.success();
        return report.with("sent", sent)
                .with("duplicate", duplicate)
                .with("failed", failed)
                .with("no_device", noDevice);
    }
}
