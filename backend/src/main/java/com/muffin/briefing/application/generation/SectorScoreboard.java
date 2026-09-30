package com.muffin.briefing.application.generation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 어제의 섹터 성적표 계산 결과. 시세를 받지 못했거나 활성 섹터가 없으면 비어 있을 수 있고, 그때는 브리핑에서 이 블록만 빠진다.
 *
 * @param gainers 가장 많이 오른 섹터(내림차순)
 * @param losers 가장 많이 내린 섹터(오름차순)
 */
public record SectorScoreboard(List<SectorScore> gainers, List<SectorScore> losers) {

    public SectorScoreboard {
        gainers = List.copyOf(gainers);
        losers = List.copyOf(losers);
    }

    /** 등락률을 계산할 수 없을 때 쓰는 빈 성적표. */
    public static SectorScoreboard empty() {
        return new SectorScoreboard(List.of(), List.of());
    }

    public boolean isEmpty() {
        return gainers.isEmpty() && losers.isEmpty();
    }

    /**
     * 섹터 한 곳의 등락률.
     *
     * @param changeRate 소수점 둘째 자리까지의 등락률(%). 상승이면 양수, 하락이면 음수다.
     */
    public record SectorScore(Long sectorId, BigDecimal changeRate) {}
}
