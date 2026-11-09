package com.muffin.briefing.presentation;

import com.muffin.briefing.application.generation.BriefingPublicationService;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.briefing-publication.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BriefingPublicationScheduler {

    private final BriefingPublicationService briefingPublicationService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    /**
     * 발행 시각(07:30)에 READY 브리핑을 공개하고, 그때 생성이 끝나지 않았으면 07:50까지 10분마다 다시 시도한다. 주말 미발행 정책은 크론의 요일 지정이
     * 담당하므로 거래일 캘린더는 참조하지 않는다. 공휴일에도 발행한다.
     */
    @Scheduled(
            cron = "${muffin.batch.briefing-publication.cron:0 30,40,50 7 * * MON-FRI}",
            zone = "${muffin.batch.briefing-publication.zone:Asia/Seoul}")
    public void publishBriefing() {
        publish();
    }

    /**
     * 08:00 이후에도 생성이 끝나지 않은 브리핑을 준비되는 대로 공개한다. 홈 카드의 "굽고 있어요" 상태가 발행 즉시 풀리게 하기 위한 재시도다. 이미
     * 공개했으면 할 일이 없으므로 건너뛴다. 09:00 이후 공개분은 알림을 보내지 않는다(NOTI-01).
     */
    @Scheduled(
            cron = "${muffin.batch.briefing-publication.catch-up-cron:0 */10 8-9 * * MON-FRI}",
            zone = "${muffin.batch.briefing-publication.zone:Asia/Seoul}")
    public void publishLateBriefing() {
        publish();
    }

    private void publish() {
        // 기준일을 여기서 한 번만 정해 로그와 실제 발행 대상이 어긋나지 않게 한다(자정 근처 경합 방지).
        LocalDateTime publishedAt = LocalDateTime.now(clock);
        LocalDate briefingDate = publishedAt.toLocalDate();
        batchJobRunner.run(BatchJob.BRIEFING_PUBLICATION, BatchTrigger.SCHEDULER, briefingDate, () -> {
            int published = briefingPublicationService.publish(briefingDate, publishedAt);
            if (published > 0) {
                return BatchJobReport.success().with("published", published);
            }
            if (briefingPublicationService.isPublished(briefingDate)) {
                return BatchJobReport.skipped("already_published");
            }
            // 공개할 브리핑이 없는 것은 이 잡의 실패가 아니라 선행 단계(브리핑 생성)의 문제다.
            return BatchJobReport.deferred("no_ready_briefing");
        });
    }
}
