package com.muffin.briefing.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muffin.briefing.application.generation.SectorScoreboard.SectorScore;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SectorScoreboardCalculatorTest {

    private static final LocalDate BRIEFING_DATE = LocalDate.of(2026, 7, 20);
    private static final LocalDate LATEST = LocalDate.of(2026, 7, 17);
    private static final LocalDate PREVIOUS = LocalDate.of(2026, 7, 16);

    private final SectorRepository sectorRepository = mock(SectorRepository.class);
    private final EtfPriceRepository etfPriceRepository = mock(EtfPriceRepository.class);
    private final SectorScoreboardCalculator calculator =
            new SectorScoreboardCalculator(sectorRepository, etfPriceRepository);

    @Test
    @DisplayName("직전 두 영업일 종가로 등락률을 계산하고 소수점 둘째 자리까지 반올림한다")
    void calculate_computesChangeRateRoundedToTwoDecimals() {
        givenTradingDates();
        givenSectors(sector(1L, 11L, "금", 1));
        // 10000 -> 10123 이면 1.23%
        givenPrices(LATEST, closed(11L, LATEST, 10_123L));
        givenPrices(PREVIOUS, closed(11L, PREVIOUS, 10_000L));

        SectorScoreboard result = calculator.calculate(BRIEFING_DATE);

        assertThat(result.gainers()).singleElement().satisfies(score -> assertThat(score.changeRate())
                .isEqualByComparingTo(new BigDecimal("1.23")));
    }

    @Test
    @DisplayName("반올림은 셋째 자리에서 올린다")
    void calculate_roundsHalfUp() {
        givenTradingDates();
        givenSectors(sector(1L, 11L, "금", 1));
        // 10000 -> 10125 이면 1.25%, 10000 -> 10126 이면 1.26%
        givenPrices(LATEST, closed(11L, LATEST, 10_126L));
        givenPrices(PREVIOUS, closed(11L, PREVIOUS, 10_000L));

        SectorScoreboard result = calculator.calculate(BRIEFING_DATE);

        assertThat(result.gainers().getFirst().changeRate()).isEqualByComparingTo(new BigDecimal("1.26"));
    }

    @Test
    @DisplayName("가장 많이 오른 2개와 가장 많이 내린 1개를 고른다")
    void calculate_picksTopTwoGainersAndTopOneLoser() {
        givenTradingDates();
        givenSectors(
                sector(1L, 11L, "금", 1), sector(2L, 12L, "방산", 2), sector(3L, 13L, "코인", 3), sector(4L, 14L, "테크", 4));
        givenPrices(
                LATEST,
                closed(11L, LATEST, 10_120L), // +1.20%
                closed(12L, LATEST, 10_080L), // +0.80%
                closed(13L, LATEST, 9_770L), // -2.30%
                closed(14L, LATEST, 9_900L)); // -1.00%
        givenPrices(
                PREVIOUS,
                closed(11L, PREVIOUS, 10_000L),
                closed(12L, PREVIOUS, 10_000L),
                closed(13L, PREVIOUS, 10_000L),
                closed(14L, PREVIOUS, 10_000L));

        SectorScoreboard result = calculator.calculate(BRIEFING_DATE);

        assertThat(result.gainers()).extracting(SectorScore::sectorId).containsExactly(1L, 2L);
        assertThat(result.losers()).extracting(SectorScore::sectorId).containsExactly(3L);
    }

    @Test
    @DisplayName("종가를 정상 수집하지 못한 섹터는 순위에서 제외한다")
    void calculate_excludesSectorWithoutSuccessfulClosingPrice() {
        givenTradingDates();
        givenSectors(sector(1L, 11L, "금", 1), sector(2L, 12L, "방산", 2));
        // 방산은 종가 수집 전(PENDING) 상태라 계산 대상이 아니다.
        givenPrices(LATEST, closed(11L, LATEST, 10_120L), EtfPrice.open(12L, LATEST, 9_000L));
        givenPrices(PREVIOUS, closed(11L, PREVIOUS, 10_000L), closed(12L, PREVIOUS, 10_000L));

        SectorScoreboard result = calculator.calculate(BRIEFING_DATE);

        assertThat(result.gainers()).extracting(SectorScore::sectorId).containsExactly(1L);
        assertThat(result.losers()).isEmpty();
    }

    @Test
    @DisplayName("한쪽 날짜의 종가만 있는 섹터는 순위에서 제외한다")
    void calculate_excludesSectorMissingOneSideOfComparison() {
        givenTradingDates();
        givenSectors(sector(1L, 11L, "금", 1), sector(2L, 12L, "방산", 2));
        givenPrices(LATEST, closed(11L, LATEST, 10_120L), closed(12L, LATEST, 10_500L));
        givenPrices(PREVIOUS, closed(11L, PREVIOUS, 10_000L));

        SectorScoreboard result = calculator.calculate(BRIEFING_DATE);

        assertThat(result.gainers()).extracting(SectorScore::sectorId).containsExactly(1L);
    }

    @Test
    @DisplayName("보합은 오르지도 내리지도 않았으므로 어느 쪽에도 넣지 않는다")
    void calculate_excludesUnchangedSector() {
        givenTradingDates();
        givenSectors(sector(1L, 11L, "금", 1));
        givenPrices(LATEST, closed(11L, LATEST, 10_000L));
        givenPrices(PREVIOUS, closed(11L, PREVIOUS, 10_000L));

        SectorScoreboard result = calculator.calculate(BRIEFING_DATE);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("등락률이 같으면 섹터 정렬 순서를 유지해 같은 입력에 같은 결과를 낸다")
    void calculate_keepsSectorOrderOnTie() {
        givenTradingDates();
        givenSectors(sector(1L, 11L, "금", 1), sector(2L, 12L, "방산", 2), sector(3L, 13L, "테크", 3));
        givenPrices(LATEST, closed(11L, LATEST, 10_100L), closed(12L, LATEST, 10_100L), closed(13L, LATEST, 10_100L));
        givenPrices(
                PREVIOUS,
                closed(11L, PREVIOUS, 10_000L),
                closed(12L, PREVIOUS, 10_000L),
                closed(13L, PREVIOUS, 10_000L));

        SectorScoreboard result = calculator.calculate(BRIEFING_DATE);

        assertThat(result.gainers()).extracting(SectorScore::sectorId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("비교할 직전 영업일이 없으면 빈 성적표를 반환한다")
    void calculate_returnsEmptyWhenNoTradingDay() {
        when(etfPriceRepository.findLatestPriceDateBefore(BRIEFING_DATE)).thenReturn(null);

        assertThat(calculator.calculate(BRIEFING_DATE).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("그 전 영업일이 없으면 등락률을 낼 수 없으므로 빈 성적표를 반환한다")
    void calculate_returnsEmptyWhenNoPreviousTradingDay() {
        when(etfPriceRepository.findLatestPriceDateBefore(BRIEFING_DATE)).thenReturn(LATEST);
        when(etfPriceRepository.findLatestPriceDateBefore(LATEST)).thenReturn(null);

        assertThat(calculator.calculate(BRIEFING_DATE).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("활성 섹터가 없으면 빈 성적표를 반환한다")
    void calculate_returnsEmptyWhenNoActiveSector() {
        givenTradingDates();
        givenSectors();
        givenPrices(LATEST);
        givenPrices(PREVIOUS);

        assertThat(calculator.calculate(BRIEFING_DATE).isEmpty()).isTrue();
    }

    private void givenTradingDates() {
        when(etfPriceRepository.findLatestPriceDateBefore(BRIEFING_DATE)).thenReturn(LATEST);
        when(etfPriceRepository.findLatestPriceDateBefore(LATEST)).thenReturn(PREVIOUS);
    }

    private void givenSectors(Sector... sectors) {
        when(sectorRepository.findByIsActiveTrueOrderBySectorOrderAsc()).thenReturn(List.of(sectors));
    }

    private void givenPrices(LocalDate priceDate, EtfPrice... prices) {
        when(etfPriceRepository.findByPriceDate(priceDate)).thenReturn(List.of(prices));
    }

    private static Sector sector(Long id, Long etfId, String name, int order) {
        Sector sector = Sector.create(1L, etfId, name, null, name, order);
        ReflectionTestUtils.setField(sector, "id", id);
        return sector;
    }

    private static EtfPrice closed(Long etfId, LocalDate priceDate, Long endPrice) {
        return EtfPrice.create(etfId, priceDate, endPrice, endPrice);
    }
}
