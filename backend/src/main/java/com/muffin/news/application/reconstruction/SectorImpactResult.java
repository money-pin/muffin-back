package com.muffin.news.application.reconstruction;

import com.muffin.news.domain.sectorimpact.enums.ImpactType;

/**
 * AI가 분석한 뉴스의 섹터별 영향도.
 *
 * @param reason 그렇게 판단한 이유 한 문장. NEUTRAL이면 빈 문자열이고, 저장 단계에서 null이 된다
 */
public record SectorImpactResult(String sectorCode, ImpactType impact, String reason) {}
