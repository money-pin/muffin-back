package com.muffin.sector.presentation;

import com.muffin.sector.infrastructure.BtcPriceCollector;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 코인 섹터 기준가 정책(매일 09:00 KST)에 따라 BTC 시세 수집을 트리거한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.batch.btc-price.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class BtcPriceScheduler {

    private final BtcPriceCollector btcPriceCollector;

    @Value("${muffin.batch.btc-price.zone:Asia/Seoul}")
    private String zone;

    @Scheduled(cron = "${muffin.batch.btc-price.cron}", zone = "${muffin.batch.btc-price.zone:Asia/Seoul}")
    public void run() {
        LocalDate date = LocalDate.now(ZoneId.of(zone));
        log.info("[coin] BTC 시세 수집 트리거 date={}", date);
        btcPriceCollector.collect(date);
    }
}
