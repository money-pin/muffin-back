package com.muffin.news.presentation.dto;

import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import java.util.List;

/** 뉴스 섹터 영향도 응답. 항상 12개 섹터를 고정 순서로 반환하며, 분석 결과가 없으면 NEUTRAL이다. */
public record NewsSectorImpactResponse(Long newsId, List<SectorImpactItem> sectorImpacts) {

    /**
     * @param reason 이 기사의 어떤 내용 때문에 해당 섹터가 오를/내릴 것으로 보는지 설명하는 한 문장. NEUTRAL이거나
     *     이 기능이 생기기 전에 분석된 기사면 null이고, 화면은 등급만 보여준다
     */
    public record SectorImpactItem(String sectorCode, String sectorName, ImpactType impact, String reason) {}
}
