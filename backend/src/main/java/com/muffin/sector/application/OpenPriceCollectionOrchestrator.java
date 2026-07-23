package com.muffin.sector.application;

import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import com.muffin.sector.infrastructure.BtcPriceCollector;
import com.muffin.sector.infrastructure.EtfPriceCollector;
import com.muffin.sector.infrastructure.EtfPriceWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenPriceCollectionOrchestrator {

    private final TradingCalendarService tradingCalendarService;
    private final BtcPriceCollector btcPriceCollector;
    private final EtfPriceCollector etfPriceCollector;
    private final EtfRepository etfRepository;
    private final EtfPriceRepository etfPriceRepository;
    private final EtfPriceWriter etfPriceWriter;
    private final ApplicationEventPublisher eventPublisher;

    public void collectOpenPrices(LocalDate priceDate) {
        boolean tradingDay = tradingCalendarService.getCalendar(priceDate).tradingDay();
        if (!tradingDay) {
            log.info("[open-price] market closed, record status priceDate={}", priceDate);
            collectSafely("BTC", priceDate, () -> btcPriceCollector.collect(priceDate, false));
            collectSafely("TOSS", priceDate, () -> etfPriceCollector.collectOpen(priceDate, false));
            return;
        }

        List<Etf> targets = etfRepository.findAll();
        if (targets.isEmpty()) {
            log.warn("[open-price] no target ETFs priceDate={}", priceDate);
            return;
        }
        if (isCompleted(targets, pricesByEtfId(priceDate))) {
            log.info("[open-price] already completed, skip priceDate={}", priceDate);
            return;
        }

        collectSafely("BTC", priceDate, () -> btcPriceCollector.collect(priceDate, true));
        collectSafely("TOSS", priceDate, () -> etfPriceCollector.collectOpen(priceDate, true));
        publishWhenCompleted(targets, priceDate);
    }

    public void finalizeMissingOpenPrices(LocalDate priceDate) {
        if (!tradingCalendarService.getCalendar(priceDate).tradingDay()) {
            log.info("[open-price] market closed, skip finalization priceDate={}", priceDate);
            return;
        }

        List<Etf> targets = etfRepository.findAll();
        if (targets.isEmpty()) {
            log.warn("[open-price] no target ETFs to finalize priceDate={}", priceDate);
            return;
        }
        Map<Long, EtfPrice> pricesByEtfId = pricesByEtfId(priceDate);
        if (isCompleted(targets, pricesByEtfId)) {
            log.info("[open-price] already finalized, skip priceDate={}", priceDate);
            return;
        }

        for (Etf target : targets) {
            if (isTerminal(pricesByEtfId.get(target.getId()))) {
                continue;
            }
            try {
                etfPriceWriter.markOpenFinalMissing(target.getId(), priceDate);
            } catch (RuntimeException exception) {
                log.error(
                        "[open-price] FINAL_MISSING save failed etfCode={} priceDate={}",
                        target.getEtfCode(),
                        priceDate,
                        exception);
            }
        }
        publishWhenCompleted(targets, priceDate);
    }

    private void collectSafely(String provider, LocalDate priceDate, Runnable collector) {
        try {
            collector.run();
        } catch (RuntimeException exception) {
            log.error("[open-price] collector failed provider={} priceDate={}", provider, priceDate, exception);
        }
    }

    private void publishWhenCompleted(List<Etf> targets, LocalDate priceDate) {
        if (!isCompleted(targets, pricesByEtfId(priceDate))) {
            log.warn("[open-price] collection incomplete priceDate={}", priceDate);
            return;
        }
        eventPublisher.publishEvent(new EtfPricesLoadedEvent(priceDate));
        log.info("[open-price] collection completed priceDate={}", priceDate);
    }

    private boolean isCompleted(List<Etf> targets, Map<Long, EtfPrice> pricesByEtfId) {
        return targets.stream().allMatch(etf -> isTerminal(pricesByEtfId.get(etf.getId())));
    }

    private Map<Long, EtfPrice> pricesByEtfId(LocalDate priceDate) {
        return etfPriceRepository.findByPriceDate(priceDate).stream()
                .collect(Collectors.toMap(EtfPrice::getEtfId, Function.identity(), (left, right) -> left));
    }

    private boolean isTerminal(EtfPrice price) {
        if (price == null) {
            return false;
        }
        PriceCollectionStatus status = price.getStartPriceStatus();
        return status == PriceCollectionStatus.SUCCESS || status == PriceCollectionStatus.FINAL_MISSING;
    }
}
