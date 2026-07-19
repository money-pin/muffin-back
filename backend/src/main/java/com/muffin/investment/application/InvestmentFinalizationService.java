package com.muffin.investment.application;

import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final SectorRepository sectorRepository;
    private final EtfPriceRepository etfPriceRepository;
    private final InvestmentFinalizationProcessor processor;

    public InvestmentFinalizationResult finalizeInvestments(LocalDate investDate, LocalDateTime finalizedAt) {
        if (!tradingCalendarService.getCalendar(investDate).tradingDay()) {
            log.info("[investment-finalization] market closed, skip investDate={}", investDate);
            return InvestmentFinalizationResult.marketClosed(investDate);
        }

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
