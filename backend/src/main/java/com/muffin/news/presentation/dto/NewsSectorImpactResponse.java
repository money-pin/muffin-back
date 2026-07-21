package com.muffin.news.presentation.dto;

import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import java.util.List;

/** 뉴스 섹터 영향도 응답. 항상 12개 섹터를 고정 순서로 반환하며, 분석 결과가 없으면 NEUTRAL이다. */
public record NewsSectorImpactResponse(Long newsSummaryId, List<SectorImpactItem> sectorImpacts) {

    public record SectorImpactItem(String sectorCode, String sectorName, ImpactType impact) {}
}
