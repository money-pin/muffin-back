package com.muffin.briefing.domain.enums;

/**
 * 간밤의 시장 블록에 표시하는 지표.
 *
 * <p>토스 지표 카탈로그는 국내 지수·국채 8종만 지원해서 해외 지수는 들어 있지 않다. S&amp;P 500과 나스닥 100은 그 지수를 추종하는
 * 미국 상장 ETF 종가로 대신한다. 지수 값 자체는 지수 제공사가 라이선스하는 데이터이지만 ETF 시세는 거래소 데이터라 성격이 다르다.
 *
 * <p>그래서 {@link #referenceSymbol()}이 있는 지표는 <b>지수 레벨이 아니라 ETF 가격</b>이다. 등락률은 지수와 거의 같지만
 * 종가 숫자가 다르므로, 화면은 "나스닥 100 (QQQ 기준)"처럼 근거를 함께 보여줘야 한다.
 */
public enum MarketIndicator {
    KOSPI("코스피", MarketIndicatorUnit.POINT, Source.INDICATOR, "KOSPI", null),
    KOSDAQ("코스닥", MarketIndicatorUnit.POINT, Source.INDICATOR, "KOSDAQ", null),
    SP500("S&P 500", MarketIndicatorUnit.USD, Source.STOCK, "SPY", "SPY"),
    NASDAQ100("나스닥 100", MarketIndicatorUnit.USD, Source.STOCK, "QQQ", "QQQ"),
    USD_KRW("원/달러", MarketIndicatorUnit.KRW, Source.EXCHANGE_RATE, null, null);

    /** 조회 방식. 토스에서 엔드포인트가 갈린다. */
    public enum Source {
        /** {@code /api/v1/market-indicators/{symbol}/candles} — 국내 지수·국채 */
        INDICATOR,
        /** {@code /api/v1/candles} — 개별 종목(국내·미국) */
        STOCK,
        /** {@code /api/v1/exchange-rate} */
        EXCHANGE_RATE
    }

    private final String displayName;
    private final MarketIndicatorUnit unit;
    private final Source source;
    private final String tossSymbol;
    private final String referenceSymbol;

    MarketIndicator(
            String displayName, MarketIndicatorUnit unit, Source source, String tossSymbol, String referenceSymbol) {
        this.displayName = displayName;
        this.unit = unit;
        this.source = source;
        this.tossSymbol = tossSymbol;
        this.referenceSymbol = referenceSymbol;
    }

    public String displayName() {
        return displayName;
    }

    public MarketIndicatorUnit unit() {
        return unit;
    }

    public Source source() {
        return source;
    }

    /** 토스에 넘길 심볼. 환율은 심볼이 없어 null이다. */
    public String tossSymbol() {
        return tossSymbol;
    }

    /** 지수를 대신해 쓴 ETF 종목코드. 지수나 환율을 직접 받는 지표는 null이다. */
    public String referenceSymbol() {
        return referenceSymbol;
    }
}
