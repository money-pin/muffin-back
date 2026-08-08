package com.muffin.investment.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.investment.application.settlement.SettlementCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ETF 시가 적재 완료 이벤트를 받아 정산을 실행하는 인바운드 어댑터(1차 트리거). 시각을 추측하지 않고 데이터가 준비된 순간 정산을 시작한다.
 *
 * <p>발행 측(ETF 로더)이 트랜잭션 안에서 발행하면 커밋 이후에 실행해 아직 커밋되지 않은 시세로 정산이 도는 것을 막는다({@code AFTER_COMMIT}). 트랜잭션
 * 없이 발행하는 경우에도 이벤트가 유실되지 않도록 즉시 실행한다({@code fallbackExecution = true}). 즉 발행 방식과 무관하게 안전하다.
 */
@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final SettlementCommandService settlementCommandService;
    private final BatchJobRunner batchJobRunner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEtfPricesLoaded(EtfPricesLoadedEvent event) {
        batchJobRunner.run(
                BatchJob.SETTLEMENT,
                BatchTrigger.EVENT,
                event.priceDate(),
                () -> SettlementBatchReports.from(settlementCommandService.settle(event.priceDate())));
    }
}
