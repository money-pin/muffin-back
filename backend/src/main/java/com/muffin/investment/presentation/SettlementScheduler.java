package com.muffin.investment.presentation;

import com.muffin.investment.application.SettlementCommandService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정산 스케줄러 안전망(2차 트리거). ETF 시세 적재 완료 이벤트가 아직 연동되지 않은 경우를 대비해 매일 지정 시각(기본 09:35)에 정산을 시도한다.
 *
 * <p>오케스트레이터가 멱등하므로(적재 가드 + SETTLED 스킵) 이벤트와 이중 실행돼도 안전하다. {@code muffin.batch.settlement.scheduler-enabled=false}로
 * 비활성화할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.batch.settlement.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SettlementScheduler {

    private final SettlementCommandService settlementCommandService;

    @Value("${muffin.batch.settlement.zone:Asia/Seoul}")
    private String zone;

    @Scheduled(cron = "${muffin.batch.settlement.cron}", zone = "${muffin.batch.settlement.zone:Asia/Seoul}")
    public void run() {
        LocalDate settlementDate = LocalDate.now(ZoneId.of(zone));
        log.info("[settlement] triggered by scheduler settlementDate={}", settlementDate);
        settlementCommandService.settle(settlementDate);
    }
}
