package com.muffin.briefing.presentation.dto;

import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.briefing.domain.enums.MarketIndicatorUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모닝 머핀 브리핑 응답.
 *
 * <p>{@code marketIndices}는 지표별로 기준일 이하의 가장 최근 정상 수신 건을 담는다. 받아오지 못한 지표는 배열에서 빠지므로
 * 카드 수가 5장보다 적을 수 있고, 프론트는 없는 카드를 "데이터 준비 중"으로 렌더링한다.
 *
 * @param isToday 요청한 날짜의 브리핑인지 여부. 주말에 금요일 브리핑으로 대체된 경우 false다
 */
public record BriefingResponse(
        LocalDate briefingDate,
        BriefingStatus status,
        boolean isToday,
        String headline,
        List<MarketIndex> marketIndices,
        List<Issue> issues,
        SectorScoreboard sectorScoreboard,
        TodayTerm term,
        Notice notice) {

    /** 아직 생성 중이거나 이용할 수 없는 상태의 응답. 화면은 상태만 보고 안내 문구를 고른다. */
    public static BriefingResponse unavailable(LocalDate briefingDate, BriefingStatus status, Notice notice) {
        return new BriefingResponse(
                briefingDate,
                status,
                true,
                null,
                List.of(),
                List.of(),
                new SectorScoreboard(List.of(), List.of()),
                null,
                notice);
    }

    /**
     * 간밤의 시장 지표 카드.
     *
     * @param unit 값의 단위. POINT(지수 포인트), KRW(원), USD(달러)
     * @param referenceSymbol 지수를 대신해 쓴 ETF 종목코드. 지수·환율을 직접 받은 지표는 null이다. 값이 있으면
     *     {@code closePrice}가 지수 레벨이 아니라 ETF 가격이므로, 화면은 "나스닥 100 (QQQ 기준)"처럼 근거를 함께 보여준다
     * @param changeRate 직전 종가 대비 등락률(%). 비교할 직전 종가가 없으면 null
     */
    public record MarketIndex(
            String name,
            BigDecimal closePrice,
            BigDecimal changeRate,
            LocalDate baseDate,
            MarketIndicatorUnit unit,
            String referenceSymbol) {}

    /**
     * @param sectors 관련 섹터 칩. NEUTRAL을 제외한 상위 2개까지만 담는다
     * @param articleAvailable 근거 기사를 열 수 있는지 여부. 기사가 아직 공개 전이거나 삭제됐으면 false이고, 화면은
     *     [기사 보기] 버튼을 숨긴다
     */
    public record Issue(
            int order,
            String title,
            String summary,
            String impactLine,
            List<SectorChip> sectors,
            Long newsId,
            boolean articleAvailable) {}

    public record SectorChip(String sectorCode, String sectorName) {}

    /** 어제의 섹터 성적표. 시세를 받지 못했으면 양쪽 모두 비어 있을 수 있다. */
    public record SectorScoreboard(List<Entry> gainers, List<Entry> losers) {}

    public record Entry(String sectorCode, String sectorName, BigDecimal changeRate) {}

    public record TodayTerm(Long termId, String term, String summary) {}

    /**
     * 하단 고정 안내. 기준 시각과 출처를 아는 쪽이 백엔드라 여기서 조립해 내려준다.
     *
     * @param sources 출처. 지표 수집이 붙기 전까지는 기사 출처만 담는다
     */
    public record Notice(String sources, LocalDateTime baseTime, String disclaimer) {}
}
