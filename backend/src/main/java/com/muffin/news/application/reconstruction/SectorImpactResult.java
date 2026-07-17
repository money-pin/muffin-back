package com.muffin.news.application.reconstruction;

import com.muffin.news.domain.sectorimpact.enums.ImpactType;

/** AI가 분석한 뉴스의 섹터별 영향도와 판단 근거. */
public record SectorImpactResult(String sectorCode, ImpactType impact, String reason) {}
