package com.muffin.briefing.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.muffin.briefing.application.generation.BriefingPublicationService;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunners;
import com.muffin.global.batch.BatchLogCapture;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BriefingPublicationSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Mock
    private BriefingPublicationService publicationService;

    @Test
    @DisplayName("늦은 발행 재시도에서 이미 공개한 날이면 미룸이 아니라 건너뜀으로 남긴다")
    void catchUp_skipsWhenAlreadyPublished() {
        BriefingPublicationScheduler scheduler = schedulerAt("2026-10-20T08:10:00+09:00");
        when(publicationService.publish(eq(TODAY), any())).thenReturn(0);
        when(publicationService.isPublished(TODAY)).thenReturn(true);

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.BRIEFING_PUBLICATION)) {
            scheduler.publishLateBriefing();

            assertThat(capture.line()).contains("outcome=skipped", "reason=already_published");
        }
    }

    @Test
    @DisplayName("아직 생성되지 않았으면 선행 단계 문제로 보고 미룸으로 남긴다")
    void catchUp_defersWhenNotReady() {
        BriefingPublicationScheduler scheduler = schedulerAt("2026-10-20T08:10:00+09:00");
        when(publicationService.publish(eq(TODAY), any())).thenReturn(0);
        when(publicationService.isPublished(TODAY)).thenReturn(false);

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.BRIEFING_PUBLICATION)) {
            scheduler.publishLateBriefing();

            assertThat(capture.line()).contains("outcome=deferred", "reason=no_ready_briefing");
        }
    }

    @Test
    @DisplayName("늦게라도 준비되면 공개한다")
    void catchUp_publishesWhenReady() {
        BriefingPublicationScheduler scheduler = schedulerAt("2026-10-20T08:10:00+09:00");
        when(publicationService.publish(eq(TODAY), any())).thenReturn(1);

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.BRIEFING_PUBLICATION)) {
            scheduler.publishLateBriefing();

            assertThat(capture.line()).contains("outcome=success", "published=1");
        }
    }

    private BriefingPublicationScheduler schedulerAt(String dateTime) {
        Clock clock = Clock.fixed(OffsetDateTime.parse(dateTime).toInstant(), ZoneId.of("Asia/Seoul"));
        return new BriefingPublicationScheduler(publicationService, BatchJobRunners.forTest(), clock);
    }
}
