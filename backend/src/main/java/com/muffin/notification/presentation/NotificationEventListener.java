package com.muffin.notification.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.global.event.BriefingPublishedEvent;
import com.muffin.global.event.NewsPublishedEvent;
import com.muffin.global.event.SettlementCompletedEvent;
import com.muffin.global.event.WeeklyRankingCreatedEvent;
import com.muffin.notification.application.send.BriefingNotificationService;
import com.muffin.notification.application.send.InvestmentResultNotificationService;
import com.muffin.notification.application.send.NewsNotificationService;
import com.muffin.notification.application.send.RankingNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 발행·정산·랭킹 확정 이벤트를 받아 알림을 보내는 인바운드 어댑터.
 *
 * <p>커밋 이후에 실행해 아직 커밋되지 않은 데이터를 읽지 않는다({@code AFTER_COMMIT}). 트랜잭션 없이 발행돼도 유실되지 않게 즉시 실행한다
 * ({@code fallbackExecution = true}). 수백 명에게 보내는 동안 발행 배치의 스레드를 붙잡지 않도록 비동기로 돈다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final BriefingNotificationService briefingNotificationService;
    private final NewsNotificationService newsNotificationService;
    private final InvestmentResultNotificationService investmentResultNotificationService;
    private final RankingNotificationService rankingNotificationService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBriefingPublished(BriefingPublishedEvent event) {
        batchJobRunner.run(
                BatchJob.NOTIFICATION_BRIEFING,
                BatchTrigger.EVENT,
                event.briefingDate(),
                () -> briefingNotificationService
                        .notifyPublished(event.briefingDate(), event.publishedAt())
                        .toReport());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNewsPublished(NewsPublishedEvent event) {
        batchJobRunner.run(
                BatchJob.NOTIFICATION_NEWS, BatchTrigger.EVENT, LocalDate.now(clock), () -> newsNotificationService
                        .notifyPublished(event.newsIds())
                        .toReport());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSettlementCompleted(SettlementCompletedEvent event) {
        batchJobRunner.run(
                BatchJob.NOTIFICATION_INVESTMENT_RESULT,
                BatchTrigger.EVENT,
                event.investDate(),
                () -> investmentResultNotificationService
                        .notifySettled(event.investDate())
                        .toReport());
    }

    /** 12:00 전에 확정되면 여기서는 건너뛰고 12:00 스케줄러가 보낸다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWeeklyRankingCreated(WeeklyRankingCreatedEvent event) {
        batchJobRunner.run(
                BatchJob.NOTIFICATION_RANKING,
                BatchTrigger.EVENT,
                LocalDate.now(clock),
                () -> rankingNotificationService.notifyIfDue().toReport());
    }
}
