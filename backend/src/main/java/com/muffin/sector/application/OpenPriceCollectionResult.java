package com.muffin.sector.application;

/**
 * 시가 수집/종결 1회 실행 결과.
 *
 * <p>수집은 09:00~09:25에 5분 간격으로 여러 번 돌면서 이미 다 받았으면 즉시 끝난다. 그래서 "성공"만으로는 정보가 없고, <b>이번 실행이 실제로 무엇을 했는지</b>가
 * 구분돼야 한다. 특히 {@link Outcome#INCOMPLETE}는 수집이 끝나지 않아 정산 트리거 이벤트가 발행되지 않은 상태라, 반복되면 그날 정산이 안 돈다.
 *
 * @param outcome 이번 실행이 무엇을 했는지
 * @param targetCount 대상 ETF 수
 */
public record OpenPriceCollectionResult(Outcome outcome, int targetCount) {

    public enum Outcome {
        /** 대상 시세를 모두 확보해 정산 트리거 이벤트를 발행했다. */
        COMPLETED,
        /** 수집을 시도했으나 아직 일부 종목이 남아 이벤트를 발행하지 않았다. */
        INCOMPLETE,
        /** 휴장일이라 상태만 기록했다. */
        MARKET_CLOSED,
        /** 이미 이번 실행 전에 모두 확보돼 있었다. */
        ALREADY_COMPLETED,
        /** 수집 대상 ETF가 등록돼 있지 않다. */
        NO_TARGETS
    }

    static OpenPriceCollectionResult of(Outcome outcome, int targetCount) {
        return new OpenPriceCollectionResult(outcome, targetCount);
    }
}
