package com.muffin.stats.application;

import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.PeriodProfitProjection;
import com.muffin.stats.application.projection.SectorHistoryProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import com.muffin.stats.domain.HistorySort;
import com.muffin.stats.domain.InvestmentType;
import com.muffin.stats.domain.SectorGroupCode;
import com.muffin.stats.domain.StatsPeriod;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse.SectorHistoryResponse;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse.SummaryResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse.GraphPointResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse.InvestmentTypeResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse.TopSectorResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수익 통계 조회 읽기 서비스. 정산 완료(SETTLED)된 투자를 실시간 집계해 누적 손익/그래프/TOP3/성향을 조립한다.
 */
@Service
@RequiredArgsConstructor
public class StatsQueryService {

    /** 모든 사용자의 초기 자본. 누적 수익률의 분모(초기자본 대비 성과)로 쓴다. */
    private static final long INITIAL_CAPITAL = 1_000_000L;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int GRAPH_DAYS = 7;
    private static final int TOP_SECTOR_COUNT = 3;

    private final StatsQueryRepository statsSummaryQueryRepository;

    /** "오늘"을 KST로 얻기 위한 전역 Clock(Asia/Seoul). date 미지정 시 현재 기간 윈도우 계산에 쓴다. */
    private final Clock clock;

    @Transactional(readOnly = true)
    public StatsSummaryResponse getSummary(Long userId) {
        List<DailyProfitProjection> dailyProfits = statsSummaryQueryRepository.findSettledDailyProfits(userId);
        if (dailyProfits.isEmpty()) {
            return new StatsSummaryResponse(null, 0L, rateOverCapital(0L), List.of(), List.of(), null);
        }

        TreeMap<LocalDate, Long> cumulativeByDate = accumulate(dailyProfits);
        LocalDate latestDate = cumulativeByDate.lastKey();
        long cumulativeAmount = cumulativeByDate.get(latestDate);

        List<SectorStatProjection> sectorStats = statsSummaryQueryRepository.findSettledSectorStats(userId);

        return new StatsSummaryResponse(
                latestDate,
                cumulativeAmount,
                rateOverCapital(cumulativeAmount),
                buildGraph(cumulativeByDate, latestDate),
                buildTopSectors(sectorStats),
                buildInvestmentType(sectorStats));
    }

    /**
     * 누적 수익 내역 조회. 선택한 기간 윈도우 안에서 발생한 정산 완료 손익을 요약하고, 종목(섹터)별 내역을 정렬해 내려준다.
     *
     * @param period 기간 탭(DAY/WEEK/MONTH/YEAR/ALL)
     * @param dateStr 조회 기준 시점. period 형식에 맞아야 하며 null/blank면 현재 윈도우. ALL이면 무시
     * @param sort 종목별 정렬 기준
     */
    @Transactional(readOnly = true)
    public ProfitHistoryResponse getHistory(Long userId, StatsPeriod period, String dateStr, HistorySort sort) {
        StatsPeriod.Window window = period.resolve(dateStr, LocalDate.now(clock));

        PeriodProfitProjection summaryStat =
                statsSummaryQueryRepository.findPeriodSummary(userId, window.start(), window.end());
        long profitAmount = valueOrZero(summaryStat == null ? null : summaryStat.totalProfitLoss());
        long totalInvestment = valueOrZero(summaryStat == null ? null : summaryStat.totalInvestment());
        SummaryResponse summary =
                new SummaryResponse(profitAmount, rateOverBaseSafe(profitAmount, totalInvestment), totalInvestment);

        List<SectorHistoryResponse> sectors =
                statsSummaryQueryRepository.findPeriodSectorStats(userId, window.start(), window.end()).stream()
                        .map(this::toSectorHistory)
                        .sorted(sort.comparator(SectorHistoryResponse::profitAmount, SectorHistoryResponse::profitRate))
                        .toList();

        boolean hasPrev =
                window.start() != null && statsSummaryQueryRepository.existsSettledBefore(userId, window.start());
        boolean hasNext = window.end() != null && statsSummaryQueryRepository.existsSettledAfter(userId, window.end());

        return new ProfitHistoryResponse(
                period.name(), window.label(), hasPrev, hasNext, summary, sort.name(), sectors);
    }

    private SectorHistoryResponse toSectorHistory(SectorHistoryProjection s) {
        long profit = valueOrZero(s.totalProfitLoss());
        long investment = valueOrZero(s.totalInvestment());
        return new SectorHistoryResponse(
                s.sectorCode(), s.sectorName(), profit, rateOverBaseSafe(profit, investment), investment);
    }

    /** 일자별 손익을 오름차순 누적해 (일자 → 그 날까지 누적손익) 맵을 만든다. */
    private TreeMap<LocalDate, Long> accumulate(List<DailyProfitProjection> dailyProfits) {
        TreeMap<LocalDate, Long> cumulativeByDate = new TreeMap<>();
        long running = 0L;
        for (DailyProfitProjection daily : dailyProfits) {
            running += daily.dailyProfitLoss();
            cumulativeByDate.put(daily.investDate(), running);
        }
        return cumulativeByDate;
    }

    /** 최근 정산일(anchor) 포함 직전 7일. 각 날짜는 그 날 이하 최신 누적을 유지(carry-forward), 최초 투자 이전은 0. */
    private List<GraphPointResponse> buildGraph(TreeMap<LocalDate, Long> cumulativeByDate, LocalDate anchor) {
        LocalDate start = anchor.minusDays(GRAPH_DAYS - 1L);
        List<GraphPointResponse> graph = new ArrayList<>(GRAPH_DAYS);
        for (int i = 0; i < GRAPH_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            Map.Entry<LocalDate, Long> entry = cumulativeByDate.floorEntry(day);
            long cumulative = (entry == null) ? 0L : entry.getValue();
            graph.add(new GraphPointResponse(day, rateOverCapital(cumulative)));
        }
        return graph;
    }

    /** 섹터별 누적 수익률 내림차순 상위 3개. 동률이면 수익금 내림차순. */
    private List<TopSectorResponse> buildTopSectors(List<SectorStatProjection> sectorStats) {
        List<TopSectorResponse> ranked = new ArrayList<>();
        List<SectorStatProjection> sorted = sectorStats.stream()
                .filter(s -> s.totalAmount() != null && s.totalAmount() > 0)
                .sorted(Comparator.comparing(
                                (SectorStatProjection s) -> rateOverBase(s.totalProfitLoss(), s.totalAmount()))
                        .thenComparing(SectorStatProjection::totalProfitLoss)
                        .reversed())
                .limit(TOP_SECTOR_COUNT)
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            SectorStatProjection s = sorted.get(i);
            ranked.add(new TopSectorResponse(
                    i + 1,
                    s.sectorCode(),
                    s.sectorName(),
                    s.totalProfitLoss(),
                    rateOverBase(s.totalProfitLoss(), s.totalAmount())));
        }
        return ranked;
    }

    /** 섹터 그룹별 누적 매수 비중으로 투자 성향을 판정한다. */
    private InvestmentTypeResponse buildInvestmentType(List<SectorStatProjection> sectorStats) {
        long total = sectorStats.stream()
                .mapToLong(s -> s.totalAmount() == null ? 0L : s.totalAmount())
                .sum();
        if (total <= 0L) {
            return null;
        }

        EnumMap<SectorGroupCode, Long> amountByGroup = new EnumMap<>(SectorGroupCode.class);
        for (SectorGroupCode code : SectorGroupCode.values()) {
            amountByGroup.put(code, 0L);
        }
        int maxSingleSectorRatio = 0;
        for (SectorStatProjection s : sectorStats) {
            SectorGroupCode code = SectorGroupCode.fromCode(s.groupCode());
            amountByGroup.merge(code, s.totalAmount(), Long::sum);
            maxSingleSectorRatio = Math.max(maxSingleSectorRatio, percent(s.totalAmount(), total));
        }

        EnumMap<SectorGroupCode, Integer> ratioByGroup = groupRatios(amountByGroup, total);
        int baseRatio = ratioByGroup.get(SectorGroupCode.BASE_ASSET);
        int techRatio = ratioByGroup.get(SectorGroupCode.FUTURE_TECH);
        int realRatio = ratioByGroup.get(SectorGroupCode.REAL_ECONOMY);

        InvestmentType type = InvestmentType.classify(techRatio, baseRatio, maxSingleSectorRatio);
        String assetRatioBullet = "%s %d%%, %s %d%%, %s %d%%"
                .formatted(
                        SectorGroupCode.BASE_ASSET.displayName(), baseRatio,
                        SectorGroupCode.FUTURE_TECH.displayName(), techRatio,
                        SectorGroupCode.REAL_ECONOMY.displayName(), realRatio);
        return InvestmentTypeResponse.of(type, assetRatioBullet);
    }

    /** 그룹별 정수 비중(%). 반올림 오차로 합이 100이 아니면 최대 비중 그룹에 차이를 배분해 합을 100으로 맞춘다. */
    private EnumMap<SectorGroupCode, Integer> groupRatios(EnumMap<SectorGroupCode, Long> amountByGroup, long total) {
        EnumMap<SectorGroupCode, Integer> ratioByGroup = new EnumMap<>(SectorGroupCode.class);
        int sum = 0;
        SectorGroupCode maxGroup = null;
        long maxAmount = -1L;
        for (Map.Entry<SectorGroupCode, Long> entry : amountByGroup.entrySet()) {
            int ratio = percent(entry.getValue(), total);
            ratioByGroup.put(entry.getKey(), ratio);
            sum += ratio;
            if (entry.getValue() > maxAmount) {
                maxAmount = entry.getValue();
                maxGroup = entry.getKey();
            }
        }
        if (sum != 100 && maxGroup != null) {
            ratioByGroup.merge(maxGroup, 100 - sum, Integer::sum);
        }
        return ratioByGroup;
    }

    /** 초기자본(100만) 대비 손익률(%), 소수 첫째자리 반올림. */
    private BigDecimal rateOverCapital(long amount) {
        return BigDecimal.valueOf(amount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(INITIAL_CAPITAL), 1, RoundingMode.HALF_UP);
    }

    /** 기준금액 대비 손익률(%), 소수 첫째자리 반올림. 섹터 손익률(누적 매수금 기준)에 쓴다. */
    private BigDecimal rateOverBase(long amount, long base) {
        return BigDecimal.valueOf(amount).multiply(HUNDRED).divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP);
    }

    /** 기준금액 대비 손익률(%). 기준금액이 0 이하(해당 기간 매수 없음)면 0.0으로 처리해 0 나눗셈을 피한다. */
    private BigDecimal rateOverBaseSafe(long amount, long base) {
        return base <= 0L ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP) : rateOverBase(amount, base);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    /** 정수 비중(%), 반올림. */
    private int percent(long amount, long total) {
        return BigDecimal.valueOf(amount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
