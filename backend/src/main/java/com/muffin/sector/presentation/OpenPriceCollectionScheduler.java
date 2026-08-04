package com.muffin.sector.presentation;

import com.muffin.sector.application.OpenPriceCollectionOrchestrator;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.batch.open-price.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OpenPriceCollectionScheduler {

    private final OpenPriceCollectionOrchestrator orchestrator;
    private final Clock clock;

    @Scheduled(
            cron = "${muffin.batch.open-price.collection-cron:0 0-15/5 9 * * *}",
            zone = "${muffin.batch.open-price.zone:Asia/Seoul}")
    public void collect() {
        LocalDate priceDate = LocalDate.now(clock);
        log.info("[open-price] collection triggered priceDate={}", priceDate);
        orchestrator.collectOpenPrices(priceDate);
    }

    @Scheduled(
            cron = "${muffin.batch.open-price.finalization-cron:0 20 9 * * *}",
            zone = "${muffin.batch.open-price.zone:Asia/Seoul}")
    public void finalizeMissing() {
        LocalDate priceDate = LocalDate.now(clock);
        log.info("[open-price] finalization triggered priceDate={}", priceDate);
        orchestrator.collectAndFinalizeOpenPrices(priceDate);
    }
}
