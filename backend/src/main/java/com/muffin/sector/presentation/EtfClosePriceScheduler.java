package com.muffin.sector.presentation;

import com.muffin.sector.infrastructure.EtfPriceCollector;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 거래일 종가를 15:35에 최초 수집하고 15:45, 15:55, 16:05에 미완료 종목만 재시도한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.batch.close-price.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class EtfClosePriceScheduler {

    private final EtfPriceCollector etfPriceCollector;
    private final Clock clock;

    @Scheduled(
            cron = "${muffin.batch.close-price.afternoon-cron:0 35,45,55 15 * * *}",
            zone = "${muffin.batch.close-price.zone:Asia/Seoul}")
    public void runAfternoon() {
        collect();
    }

    @Scheduled(
            cron = "${muffin.batch.close-price.final-cron:0 5 16 * * *}",
            zone = "${muffin.batch.close-price.zone:Asia/Seoul}")
    public void runFinalAttempt() {
        collect();
    }

    private void collect() {
        LocalDate date = LocalDate.now(clock);
        log.info("[close-price] collection triggered date={}", date);
        etfPriceCollector.collectClose(date);
    }
}
