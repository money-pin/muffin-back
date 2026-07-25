package com.muffin.investment.application.settlement;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
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
 * 정산 배치 오케스트레이터. 트랜잭션 없이 대상을 조회하고 사용자별로 두 phase에 위임한다(각 phase가 유저별 독립 트랜잭션).
 *
 * <p>스프링 배치 대신 스케줄러/이벤트 + 서비스 루프로 구성한다. 재처리 안전성은 settlement_status(PENDING/FAILED만 대상, 종료 상태 스킵)로, 실패 격리는
 * 사용자별 트랜잭션으로 보장한다.
 *
 * <p>phase 분리: <b>phase 1</b>({@link SettlementSnapshotProcessor})은 EtfPrice의 매수가(전일 종가)/매도가(당일 시가)
 * status를 판단해 investment_sector에 스냅샷을 남기고, <b>phase 2</b>({@link SettlementAggregationProcessor})는 그
 * 스냅샷만 읽어 손익을 집계하고 user_asset에 반영한다. EtfPrice 의존은 phase 1에 격리된다.
 *
 * <p>대상 분기: 투자하지 않은 날(NO_INVEST)은 손익 0으로 종료(NO_SETTLEMENT), 확정 투자 중 직전 거래일 건은 스냅샷 후 정산, 정산 창을 놓친 오래된 확정
 * 투자는 취소(CANCELLED)한다. 일시적 오류(락 충돌 등)만 최대 3회 재시도하고, 결정적 오류는 즉시 FAILED로 둔다.
 *
 * <p>진입 스킵은 <b>휴장/미적재</b>만 본다: 활성 ETF 시가가 전부 MARKET_CLOSED(휴장)이거나 한 행도 없으면(미적재) 정산하지 않는다. 특정 ETF가 미확보라도
 * 다른 투자는 막지 않으며, 그 미확보 ETF를 쓴 섹터만 phase 1에서 0% 폴백된다(한 ETF가 전체를 블록하지 않는다).
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
    private final TradingCalendarService tradingCalendarService;
    private final SettlementSnapshotProcessor snapshotProcessor;
    private final SettlementAggregationProcessor aggregationProcessor;

    /**
     * 지정 일자의 ETF 시가로 미정산(PENDING/FAILED) 건을 정산/취소/미정산 처리한다.
     *
     * @param settlementDate 당일 시가 기준 일자. 이 일자 이전(invest_date &lt; settlementDate)의 미정산 건이 대상.
     */
    public SettlementBatchResult settle(LocalDate settlementDate) {
        List<Sector> sectors = sectorRepository.findAll();
        List<EtfPrice> openPrices = etfPriceRepository.findByPriceDate(settlementDate);

        // 휴장/미적재 스킵: 활성 ETF 시가가 전부 MARKET_CLOSED이거나 한 행도 없으면 정산하지 않는다.
        if (!hasTradingSignal(sectors, openPrices)) {
            log.info("[settlement] market closed or open prices not loaded for {}, skip settlement", settlementDate);
            return SettlementBatchResult.skipped(settlementDate);
        }

        List<Investment> targets = investmentRepository.findByStatusInAndSettlementStatusInAndInvestDateLessThan(
                TARGET_STATUSES, REPROCESSABLE, settlementDate);
        if (targets.isEmpty()) {
            log.info("[settlement] no targets for {}", settlementDate);
            return new SettlementBatchResult(settlementDate, true, 0, 0, 0);
        }

        LocalDate prevTradingDay =
                tradingCalendarService.getCalendar(settlementDate).previousTradingDay();

        Map<Long, Long> sectorToEtfId = sectors.stream().collect(Collectors.toMap(Sector::getId, Sector::getEtfId));
        Map<Long, EtfPrice> openPriceByEtfId =
                openPrices.stream().collect(Collectors.toMap(EtfPrice::getEtfId, Function.identity(), (a, b) -> a));
        Map<Long, BigDecimal> closePriceByEtfId = closePricesByEtfId(prevTradingDay);

        int success = 0;
        int failed = 0;
        List<Investment> pending = new ArrayList<>(targets);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !pending.isEmpty(); attempt++) {
            boolean lastAttempt = attempt == MAX_ATTEMPTS;
            List<Investment> retryNext = new ArrayList<>();
            for (Investment target : pending) {
                try {
                    dispatch(target, prevTradingDay, sectorToEtfId, closePriceByEtfId, openPriceByEtfId);
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
            Map<Long, BigDecimal> closePriceByEtfId,
            Map<Long, EtfPrice> openPriceByEtfId) {
        if (target.getStatus() == InvestmentStatus.NO_INVEST) {
            aggregationProcessor.recordNoSettlement(target.getId());
            return;
        }
        if (isStale(target, prevTradingDay)) {
            aggregationProcessor.cancel(target.getId());
            return;
        }
        // 확정·직전 거래일 건: phase 1(스냅샷) → phase 2(집계).
        snapshotProcessor.stamp(target.getId(), sectorToEtfId, closePriceByEtfId, openPriceByEtfId);
        aggregationProcessor.settle(target.getId());
    }

    /**
     * 거래일 신호가 하나라도 있는지 확인한다. 활성 ETF 중 시가 행이 있고 그 status가 MARKET_CLOSED가 아닌 게 하나라도 있으면 거래일로 보고 진행한다. 전부
     * MARKET_CLOSED(휴장)이거나 한 행도 없으면(미적재) 스킵한다.
     */
    private boolean hasTradingSignal(List<Sector> sectors, List<EtfPrice> openPrices) {
        Set<Long> activeEtfIds =
                sectors.stream().filter(Sector::isActive).map(Sector::getEtfId).collect(Collectors.toSet());
        if (activeEtfIds.isEmpty()) {
            return false; // 활성 섹터 설정 전이면 정산하지 않는다.
        }
        Map<Long, EtfPrice> priceByEtfId =
                openPrices.stream().collect(Collectors.toMap(EtfPrice::getEtfId, Function.identity(), (a, b) -> a));
        return activeEtfIds.stream().anyMatch(etfId -> {
            EtfPrice price = priceByEtfId.get(etfId);
            return price != null && price.getStartPriceStatus() != PriceCollectionStatus.MARKET_CLOSED;
        });
    }

    /** 매수가 스냅샷용: 전일 거래일의 종가 중 SUCCESS 확정분만 etfId로 매핑한다. */
    private Map<Long, BigDecimal> closePricesByEtfId(LocalDate prevTradingDay) {
        return etfPriceRepository.findByPriceDate(prevTradingDay).stream()
                .filter(price ->
                        price.getEndPriceStatus() == PriceCollectionStatus.SUCCESS && price.getEndPrice() != null)
                .collect(Collectors.toMap(
                        EtfPrice::getEtfId, price -> BigDecimal.valueOf(price.getEndPrice()), (a, b) -> a));
    }

    /** 투자일자가 직전 거래일보다 이르면 정산 창을 놓친 것으로 본다(취소 대상). */
    private boolean isStale(Investment investment, LocalDate prevTradingDay) {
        return investment.getInvestDate().isBefore(prevTradingDay);
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
            aggregationProcessor.markFailed(investmentId);
        } catch (Exception ex) {
            log.error("[settlement] markFailed error investmentId={}", investmentId, ex);
        }
    }
}
