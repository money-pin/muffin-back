package com.muffin.sector.application;

/**
 * 시가 수집/종결 1회 실행 결과.
 *
 * <p>수집은 09:00~09:15에 5분 간격으로 여러 번 돌고, 09:20 종결 실행이 마지막으로 한 번 더 수집한 뒤 못 받은 종목을 확정한다. 그래서 "성공"만으로는 정보가 없고,
 * <b>이번 실행이 실제로 무엇을 했는지</b>가 구분돼야 한다. 특히 {@link Outcome#INCOMPLETE}는 종결까지 하고도 시세가 다 차지 않아 정산 트리거 이벤트가
 * 발행되지 않은 상태라, 그날 정산이 돌지 않는다.
 *
 * @param outcome 이번 실행이 무엇을 했는지
 * @param targetCount 대상 ETF 수
 */
public record OpenPriceCollectionResult(Outcome outcome, int targetCount) {

    public enum Outcome {
        /** 수집만 하는 단계라 정상 수행했다. 이 단계는 이벤트를 발행하지 않는다. */
        COLLECTED,
        /** 종결까지 마쳐 대상 시세를 모두 확보했고 정산 트리거 이벤트를 발행했다. */
        COMPLETED,
        /** 종결했는데도 시세가 다 차지 않아 이벤트를 발행하지 못했다. */
        INCOMPLETE,
        /** 휴장일이라 상태만 기록했다. */
        MARKET_CLOSED,
        /** 수집 대상 ETF가 등록돼 있지 않다. */
        NO_TARGETS
    }

    static OpenPriceCollectionResult of(Outcome outcome, int targetCount) {
        return new OpenPriceCollectionResult(outcome, targetCount);
    }
}
