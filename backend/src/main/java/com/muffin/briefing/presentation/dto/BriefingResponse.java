package com.muffin.briefing.presentation.dto;

import com.muffin.briefing.domain.enums.BriefingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모닝 머핀 브리핑 응답.
 *
 * <p>{@code marketIndices}는 자리만 잡아 둔 것으로 지금은 항상 빈 배열이다. 시장 지표 수집이 별도 스펙이라, 그 파이프라인이
 * 붙으면 이 배열만 채워진다. 프론트는 비어 있을 때 "데이터 준비 중"으로 렌더링한다.
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

    /** 간밤의 시장 지표. 별도 스펙이 붙기 전까지 사용되지 않는다. */
    public record MarketIndex(String name, BigDecimal closePrice, BigDecimal changeRate, LocalDate baseDate) {}

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
