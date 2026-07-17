package com.muffin.sector.domain.etf;

/** ETF 시세를 조회하는 외부 데이터 제공처. 수집기가 자신이 담당할 종목만 골라 조회하는 데 사용한다. */
public enum PriceProvider {
    TOSS,
    COINGECKO
}
