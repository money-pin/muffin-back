package com.muffin.investment.application;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

/**
 * 정산 배치 오케스트레이터. 트랜잭션 없이 대상을 조회하고 사용자별로 {@link SettlementUserProcessor}에 위임한다(각 사용자가 독립 트랜잭션).
 *
 * <p>스프링 배치 대신 스케줄러/이벤트 + 서비스 루프로 구성한다. 재처리 안전성은 settlement_status(PENDING/FAILED만 대상, 종료 상태 스킵)로,
 * 실패 격리는 사용자별 트랜잭션으로 보장한다.
 *
 * <p>대상 분기: 투자하지 않은 날(NO_INVEST)은 손익 0으로 종료(NO_SETTLEMENT), 확정 투자 중 직전 거래일 건은 당일 시가로 정산, 정산 창을 놓친 오래된 확정 투자는
 * 취소(CANCELLED)한다. 일시적 오류(락 충돌 등)만 최대 3회 재시도하고, 결정적 오류는 즉시 FAILED로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCommandService {

    private static final int MAX_ATTEMPTS = 3;
    private static final List<InvestmentStatus> TARGET_STATUSES =
            List.of(InvestmentStatus.CONFIRMED, InvestmentStatus.NO_INVEST);
    private static final List<SettlementStatus> REPROCESSABLE =
            List.of(SettlementStatus.PENDING, SettlementStatus.FAILED);

    private final InvestmentRepository investmentRepository;
    private final EtfPriceRepository etfPriceRepository;
    private final SectorRepository sectorRepository;
    private final SettlementUserProcessor processor;

    /**
     * 지정 일자의 ETF 시가로 미정산(PENDING/FAILED) 건을 정산/취소/미정산 처리한다.
     *
     * @param settlementDate 당일 시가 기준 일자. 이 일자 이전(invest_date &lt; settlementDate)의 미정산 건이 대상.
     */
    public SettlementBatchResult settle(LocalDate settlementDate) {
        List<Sector> sectors = sectorRepository.findAll();
        List<EtfPrice> prices = etfPriceRepository.findByPriceDate(settlementDate);

        // 시가 준비 가드: 행 존재만으로는 PENDING/NO_DATA/FAILED를 구분할 수 없으므로 상태까지 확인한다.
        if (!openPricesReady(sectors, prices)) {
            log.warn("[settlement] ETF open prices not ready for {}, skip settlement", settlementDate);
            return SettlementBatchResult.skipped(settlementDate);
        }

        List<Investment> allTargets = investmentRepository.findByStatusInAndSettlementStatusInAndInvestDateLessThan(
                TARGET_STATUSES, REPROCESSABLE, settlementDate);
        // 직전 거래일. 확정 투자가 "정산 창을 놓쳤는지(stale)" 판정에 사용한다.
        LocalDate prevTradingDay = etfPriceRepository.findLatestPriceDateBefore(settlementDate);
        List<Investment> targets = filterProcessable(allTargets, prevTradingDay, settlementDate);
        if (targets.isEmpty()) {
            log.info("[settlement] no targets for {}", settlementDate);
            return new SettlementBatchResult(settlementDate, true, 0, 0, 0);
        }

        Map<Long, Long> sectorToEtfId = sectors.stream().collect(Collectors.toMap(Sector::getId, Sector::getEtfId));
        Map<Long, EtfPrice> etfPriceByEtfId =
                prices.stream().collect(Collectors.toMap(EtfPrice::getEtfId, Function.identity(), (a, b) -> a));

        int success = 0;
        int failed = 0;
        List<Investment> pending = new ArrayList<>(targets);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !pending.isEmpty(); attempt++) {
            boolean lastAttempt = attempt == MAX_ATTEMPTS;
            List<Investment> retryNext = new ArrayList<>();
            for (Investment target : pending) {
                try {
                    dispatch(target, prevTradingDay, sectorToEtfId, etfPriceByEtfId);
                    success++;
                } catch (Exception e) {
                    if (!lastAttempt && isRetryable(e)) {
                        retryNext.add(target); // 일시적 오류만 다음 패스에서 재시도
                        log.warn(
                                "[settlement] retryable failure investmentId={} attempt={}",
                                target.getId(),
                                attempt,
                                e);
                    } else {
                        failed++;
                        log.error(
                                "[settlement] failed investmentId={} userId={}", target.getId(), target.getUserId(), e);
                        markFailedSafely(target.getId());
                    }
                }
            }
            pending = retryNext;
        }

        SettlementBatchResult result = new SettlementBatchResult(settlementDate, true, targets.size(), success, failed);
        log.info("[settlement] done {}", result);
        return result;
    }

    private void dispatch(
            Investment target,
            LocalDate prevTradingDay,
            Map<Long, Long> sectorToEtfId,
            Map<Long, EtfPrice> etfPriceByEtfId) {
        if (target.getStatus() == InvestmentStatus.NO_INVEST) {
            processor.recordNoSettlement(target.getId());
        } else if (isStale(target, prevTradingDay)) {
            processor.cancel(target.getId());
        } else {
            processor.settle(target.getId(), sectorToEtfId, etfPriceByEtfId);
        }
    }

    /** 활성 ETF의 시가가 정상 수집됐거나 마감 시각 이후 FINAL_MISSING으로 확정됐는지 확인한다. */
    private boolean openPricesReady(List<Sector> sectors, List<EtfPrice> prices) {
        Set<Long> activeEtfIds =
                sectors.stream().filter(Sector::isActive).map(Sector::getEtfId).collect(Collectors.toSet());
        if (activeEtfIds.isEmpty()) {
            return false; // 활성 섹터 설정 전이면 정산하지 않는다.
        }
        Map<Long, EtfPrice> priceByEtfId =
                prices.stream().collect(Collectors.toMap(EtfPrice::getEtfId, Function.identity(), (a, b) -> a));
        return activeEtfIds.stream().allMatch(etfId -> {
            EtfPrice price = priceByEtfId.get(etfId);
            if (price == null) {
                return false;
            }
            PriceCollectionStatus status = price.getStartPriceStatus();
            return status == PriceCollectionStatus.SUCCESS || status == PriceCollectionStatus.FINAL_MISSING;
        });
    }

    /**
     * 이번 실행에서 처리 가능한 대상만 남긴다. 직전 거래일을 구할 수 없으면(첫 적재일/짧은 보존 기간) 확정 투자의 정산 창을 판정할 수 없으므로, 확정 건은 PENDING으로
     * 남겨 다음 실행으로 미루고 NO_INVEST만 종료 처리한다.
     */
    private List<Investment> filterProcessable(
            List<Investment> targets, LocalDate prevTradingDay, LocalDate settlementDate) {
        if (prevTradingDay != null) {
            return targets;
        }
        long deferred = targets.stream()
                .filter(t -> t.getStatus() == InvestmentStatus.CONFIRMED)
                .count();
        if (deferred > 0) {
            log.warn(
                    "[settlement] no previous trading day before {}, defer {} CONFIRMED (kept PENDING)",
                    settlementDate,
                    deferred);
        }
        return targets.stream()
                .filter(t -> t.getStatus() == InvestmentStatus.NO_INVEST)
                .toList();
    }

    /** 투자일자가 직전 거래일보다 이르면 정산 창을 놓친 것으로 본다(취소 대상). */
    private boolean isStale(Investment investment, LocalDate prevTradingDay) {
        return prevTradingDay != null && investment.getInvestDate().isBefore(prevTradingDay);
    }

    /** 재시도해 볼 만한 일시적 오류인지(락 충돌/데드락/타임아웃 등). 결정적 오류는 재시도하지 않는다. */
    private boolean isRetryable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TransientDataAccessException || t instanceof OptimisticLockException) {
                return true;
            }
        }
        return false;
    }

    private void markFailedSafely(Long investmentId) {
        try {
            processor.markFailed(investmentId);
        } catch (Exception ex) {
            log.error("[settlement] markFailed error investmentId={}", investmentId, ex);
        }
    }
}
