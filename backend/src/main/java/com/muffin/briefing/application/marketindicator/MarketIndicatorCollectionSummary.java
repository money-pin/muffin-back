package com.muffin.briefing.application.marketindicator;

/**
 * 지표 수집 결과.
 *
 * @param succeeded 정상 수신한 지표 수
 * @param failed 받아오지 못한 지표 수
 */
public record MarketIndicatorCollectionSummary(int succeeded, int failed) {

    public boolean hasAnySuccess() {
        return succeeded > 0;
    }
}
