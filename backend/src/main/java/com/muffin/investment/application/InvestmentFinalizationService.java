package com.muffin.investment.application;

import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 거래일 여부와 종가 스냅샷을 한 번 준비한 뒤 사용자별 독립 트랜잭션으로 자정 마감을 위임한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentFinalizationService {

    private final TradingCalendarService tradingCalendarService;
    private final UserAssetRepository userAssetRepository;
    private final InvestmentRepository investmentRepository;
    private final SectorRepository sectorRepository;
    private final EtfPriceRepository etfPriceRepository;
    private final InvestmentFinalizationProcessor processor;

    /**
     * 가장 최근 마감 대상일부터 과거의 연속된 미완료 거래일을 찾아 오래된 날짜부터 복구한다.
     *
     * <p>오래된 날짜가 여전히 불완전하면 더 최신 날짜를 먼저 완료하지 않는다. 이 순서를 지켜야 다음 실행에서도 최신 날짜를 출발점으로
     * 과거의 미완료 날짜까지 다시 도달할 수 있다.
     */
    public List<InvestmentFinalizationResult> finalizePendingDates(
            LocalDate latestInvestDate, LocalDateTime finalizedAt) {
        List<LocalDate> pendingDates = findConsecutivePendingDates(latestInvestDate);
        Collections.reverse(pendingDates);

        List<InvestmentFinalizationResult> results = new ArrayList<>();
        for (LocalDate investDate : pendingDates) {
            InvestmentFinalizationResult result = finalizeTradingDay(investDate, finalizedAt);
            results.add(result);
            if (!isFinalizationComplete(investDate)) {
                log.warn(
                        "[investment-finalization] date remains incomplete, stop newer dates investDate={}",
                        investDate);
                break;
            }
        }
        return List.copyOf(results);
    }

    public InvestmentFinalizationResult finalizeInvestments(LocalDate investDate, LocalDateTime finalizedAt) {
        if (!tradingCalendarService.getCalendar(investDate).tradingDay()) {
            log.info("[investment-finalization] market closed, skip investDate={}", investDate);
            return InvestmentFinalizationResult.marketClosed(investDate);
        }

        return finalizeTradingDay(investDate, finalizedAt);
    }

    private List<LocalDate> findConsecutivePendingDates(LocalDate latestInvestDate) {
        List<LocalDate> pendingDates = new ArrayList<>();
        LocalDate candidate = latestInvestDate;

        while (true) {
            TradingCalendar calendar = tradingCalendarService.getCalendar(candidate);
            if (calendar.tradingDay()) {
                if (isFinalizationComplete(candidate)) {
                    return pendingDates;
                }
                pendingDates.add(candidate);
            }
            candidate = calendar.previousTradingDay();
        }
    }

    private boolean isFinalizationComplete(LocalDate investDate) {
        LocalDateTime cutoff = investDate.plusDays(1).atStartOfDay();
        long targetCount = userAssetRepository.countByCreatedAtBefore(cutoff);
        if (targetCount == 0L) {
            return true;
        }
        long completedCount = investmentRepository.countCompletedFinalizationsByInvestDate(investDate);
        return completedCount == targetCount;
    }

    private InvestmentFinalizationResult finalizeTradingDay(LocalDate investDate, LocalDateTime finalizedAt) {

        LocalDateTime cutoff = investDate.plusDays(1).atStartOfDay();
        var targets = userAssetRepository.findByCreatedAtBefore(cutoff);
        Map<Long, BigDecimal> closePricesBySectorId = closePricesBySectorId(investDate);

        int success = 0;
        int failure = 0;
        for (UserAsset target : targets) {
            try {
                processor.finalizeUser(target.getId(), investDate, finalizedAt, closePricesBySectorId);
                success++;
            } catch (RuntimeException exception) {
                failure++;
                log.error(
                        "[investment-finalization] failed userAssetId={} investDate={}",
                        target.getId(),
                        investDate,
                        exception);
            }
        }

        InvestmentFinalizationResult result =
                new InvestmentFinalizationResult(investDate, true, targets.size(), success, failure);
        log.info("[investment-finalization] done {}", result);
        return result;
    }

    private Map<Long, BigDecimal> closePricesBySectorId(LocalDate investDate) {
        Map<Long, EtfPrice> pricesByEtfId = etfPriceRepository.findByPriceDate(investDate).stream()
                .filter(price -> price.getEndPriceStatus() == PriceCollectionStatus.SUCCESS)
                .collect(Collectors.toMap(EtfPrice::getEtfId, Function.identity(), (left, right) -> left));

        return sectorRepository.findAll().stream()
                .filter(sector -> pricesByEtfId.containsKey(sector.getEtfId()))
                .collect(Collectors.toUnmodifiableMap(
                        sector -> sector.getId(),
                        sector -> BigDecimal.valueOf(
                                pricesByEtfId.get(sector.getEtfId()).getEndPrice())));
    }
}
