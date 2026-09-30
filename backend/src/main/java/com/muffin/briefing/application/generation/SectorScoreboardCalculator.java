package com.muffin.briefing.application.generation;

import com.muffin.briefing.application.generation.SectorScoreboard.SectorScore;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 어제의 섹터 성적표를 계산한다. 브리핑에서 유일하게 숫자를 다루는 지점이며, AI는 이 결과를 받아 문장만 쓴다.
 *
 * <p>등락률 = (직전 영업일 종가 − 그 전 영업일 종가) / 그 전 영업일 종가. 기획서의 섹터 성적표 정의를 그대로 따른다.
 *
 * <p>계산할 수 없는 섹터는 순위에서 조용히 빠진다. 종가를 정상 수집하지 못했거나(SUCCESS가 아니거나 값이 없음) 비활성인 섹터가
 * 여기에 해당한다. 한 섹터의 시세 누락 때문에 성적표 전체가 사라지면 안 되기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectorScoreboardCalculator {

    private static final int TOP_GAINER_COUNT = 2;
    private static final int TOP_LOSER_COUNT = 1;

    /** 나눗셈 중간 정밀도. 최종 결과는 소수점 둘째 자리로 반올림한다. */
    private static final int DIVISION_SCALE = 10;

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final SectorRepository sectorRepository;
    private final EtfPriceRepository etfPriceRepository;

    /**
     * 기준일 직전 두 영업일의 종가로 활성 섹터의 등락률을 구하고 상승 2개·하락 1개를 고른다.
     *
     * @param briefingDate 브리핑 발행일. 이 날짜 이전의 종가만 사용한다.
     */
    public SectorScoreboard calculate(LocalDate briefingDate) {
        LocalDate latestDate = etfPriceRepository.findLatestPriceDateBefore(briefingDate);
        if (latestDate == null) {
            log.warn("Sector scoreboard skipped: no closing price before {}", briefingDate);
            return SectorScoreboard.empty();
        }

        LocalDate previousDate = etfPriceRepository.findLatestPriceDateBefore(latestDate);
        if (previousDate == null) {
            log.warn("Sector scoreboard skipped: no closing price before {}", latestDate);
            return SectorScoreboard.empty();
        }

        Map<Long, Long> latestPrices = closingPricesByEtfId(latestDate);
        Map<Long, Long> previousPrices = closingPricesByEtfId(previousDate);

        List<SectorScore> scores = new ArrayList<>();
        for (Sector sector : sectorRepository.findByIsActiveTrueOrderBySectorOrderAsc()) {
            Long latest = latestPrices.get(sector.getEtfId());
            Long previous = previousPrices.get(sector.getEtfId());
            if (latest == null || previous == null || previous == 0L) {
                continue;
            }
            scores.add(new SectorScore(sector.getId(), changeRate(latest, previous)));
        }

        if (scores.isEmpty()) {
            log.warn("Sector scoreboard is empty: latestDate={}, previousDate={}", latestDate, previousDate);
            return SectorScoreboard.empty();
        }
        return new SectorScoreboard(pickGainers(scores), pickLosers(scores));
    }

    /** 해당 일자에 정상 수집된 종가만 etfId 기준으로 모은다. */
    private Map<Long, Long> closingPricesByEtfId(LocalDate priceDate) {
        Map<Long, Long> prices = new HashMap<>();
        for (EtfPrice price : etfPriceRepository.findByPriceDate(priceDate)) {
            if (price.getEndPriceStatus() == PriceCollectionStatus.SUCCESS && price.getEndPrice() != null) {
                prices.put(price.getEtfId(), price.getEndPrice());
            }
        }
        return prices;
    }

    private static BigDecimal changeRate(long latest, long previous) {
        return BigDecimal.valueOf(latest - previous)
                .divide(BigDecimal.valueOf(previous), DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENT)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 오른 섹터만 등락률 내림차순으로 고른다. 보합(0%)은 "올랐다"고 할 수 없어 제외한다. 동률이면 계산 순서(섹터 정렬 순서)를 유지해
     * 같은 입력에 같은 결과가 나오게 한다.
     */
    private static List<SectorScore> pickGainers(List<SectorScore> scores) {
        return scores.stream()
                .filter(score -> score.changeRate().signum() > 0)
                .sorted(Comparator.comparing(SectorScore::changeRate).reversed())
                .limit(TOP_GAINER_COUNT)
                .toList();
    }

    /** 내린 섹터만 등락률 오름차순으로 고른다. 보합(0%)은 제외한다. */
    private static List<SectorScore> pickLosers(List<SectorScore> scores) {
        return scores.stream()
                .filter(score -> score.changeRate().signum() < 0)
                .sorted(Comparator.comparing(SectorScore::changeRate))
                .limit(TOP_LOSER_COUNT)
                .toList();
    }
}
