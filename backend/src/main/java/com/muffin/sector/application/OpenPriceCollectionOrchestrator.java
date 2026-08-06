package com.muffin.sector.application;

import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.sector.application.OpenPriceCollectionResult.Outcome;
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

    /**
     * @return 이번 실행이 무엇을 했는지. 호출자가 배치 실행 로그에 남긴다.
     */
    public OpenPriceCollectionResult collectOpenPrices(LocalDate priceDate) {
        boolean tradingDay = tradingCalendarService.getCalendar(priceDate).tradingDay();
        if (!tradingDay) {
            collectSafely("BTC", priceDate, () -> btcPriceCollector.collect(priceDate, false));
            collectSafely("TOSS", priceDate, () -> etfPriceCollector.collectOpen(priceDate, false));
            return OpenPriceCollectionResult.of(Outcome.MARKET_CLOSED, 0);
        }

        List<Etf> targets = etfRepository.findAll();
        if (targets.isEmpty()) {
            log.warn("[open-price] no target ETFs priceDate={}", priceDate);
            return OpenPriceCollectionResult.of(Outcome.NO_TARGETS, 0);
        }
        if (isCompleted(targets, pricesByEtfId(priceDate))) {
            return OpenPriceCollectionResult.of(Outcome.ALREADY_COMPLETED, targets.size());
        }

        collectSafely("BTC", priceDate, () -> btcPriceCollector.collect(priceDate, true));
        collectSafely("TOSS", priceDate, () -> etfPriceCollector.collectOpen(priceDate, true));
        return publishWhenCompleted(targets, priceDate);
    }

    /**
     * @return 이번 실행이 무엇을 했는지. 호출자가 배치 실행 로그에 남긴다.
     */
    public OpenPriceCollectionResult finalizeMissingOpenPrices(LocalDate priceDate) {
        if (!tradingCalendarService.getCalendar(priceDate).tradingDay()) {
            return OpenPriceCollectionResult.of(Outcome.MARKET_CLOSED, 0);
        }

        List<Etf> targets = etfRepository.findAll();
        if (targets.isEmpty()) {
            log.warn("[open-price] no target ETFs to finalize priceDate={}", priceDate);
            return OpenPriceCollectionResult.of(Outcome.NO_TARGETS, 0);
        }
        Map<Long, EtfPrice> pricesByEtfId = pricesByEtfId(priceDate);
        if (isCompleted(targets, pricesByEtfId)) {
            return OpenPriceCollectionResult.of(Outcome.ALREADY_COMPLETED, targets.size());
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
        return publishWhenCompleted(targets, priceDate);
    }

    private void collectSafely(String provider, LocalDate priceDate, Runnable collector) {
        try {
            collector.run();
        } catch (RuntimeException exception) {
            log.error("[open-price] collector failed provider={} priceDate={}", provider, priceDate, exception);
        }
    }

    private OpenPriceCollectionResult publishWhenCompleted(List<Etf> targets, LocalDate priceDate) {
        if (!isCompleted(targets, pricesByEtfId(priceDate))) {
            return OpenPriceCollectionResult.of(Outcome.INCOMPLETE, targets.size());
        }
        eventPublisher.publishEvent(new EtfPricesLoadedEvent(priceDate));
        return OpenPriceCollectionResult.of(Outcome.COMPLETED, targets.size());
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
