package com.muffin.scrap.presentation.dto;

import java.time.OffsetDateTime;

/**
 * 스크랩/해제 결과 응답. 스크랩 시 최초 저장 시각(KST)을 함께 내려주고, 해제 시 {@code scrappedAt}은 null이다.
 *
 * @param newsId 대상 뉴스 ID
 * @param isScrapped 스크랩 상태(스크랩=true, 해제=false)
 * @param scrappedAt 최초 스크랩 시각(KST). 해제 응답에서는 null
 */
public record ScrapResponse(Long newsId, boolean isScrapped, OffsetDateTime scrappedAt) {

    public static ScrapResponse scrapped(Long newsId, OffsetDateTime scrappedAt) {
        return new ScrapResponse(newsId, true, scrappedAt);
    }

    public static ScrapResponse unscrapped(Long newsId) {
        return new ScrapResponse(newsId, false, null);
    }
}
