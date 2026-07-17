package com.muffin.sector.application.seed;

import com.muffin.sector.domain.etf.PriceProvider;
import java.util.List;

/** 섹터/ETF 기준 데이터 상수. §4의 시딩 데이터를 그대로 옮긴 것으로, DB나 Spring에 의존하지 않는다. */
public final class SectorSeedData {

    private SectorSeedData() {}

    public record GroupSeed(String groupCode, String name, String description, int groupOrder) {}

    public record EtfSeed(String etfCode, String etfName, PriceProvider priceProvider) {}

    public record SectorSeed(
            String sectorCode, String groupCode, String etfCode, String name, String description, int sectorOrder) {}

    /** §4.1~4.3의 3개 섹터 그룹. */
    public static final List<GroupSeed> GROUPS = List.of(
            new GroupSeed("BASE_ASSET", "기초자산", null, 1),
            new GroupSeed("FUTURE_TECH", "미래 기술&혁신", null, 2),
            new GroupSeed("REAL_ECONOMY", "실물 경제&인프라", null, 3));

    /**
     * §4.1~4.3에서 확정된 11개 ETF와 코인 섹터 기준가 정책에 따른 BTC 1건. `BTC`는 토스증권이 아닌 CoinGecko API로
     * 시세를 수집하므로({@link PriceProvider#COINGECKO}) 종목코드 대신 CoinGecko 코인 id 대응 코드를 쓰고,
     * {@link com.muffin.sector.infrastructure.EtfPriceCollector}(토스 전용)의 수집 대상에서 제외된다.
     */
    public static final List<EtfSeed> ETFS = List.of(
            new EtfSeed("459580", "KODEX CD금리액티브(합성)", PriceProvider.TOSS),
            new EtfSeed("132030", "KODEX 골드선물(H)", PriceProvider.TOSS),
            new EtfSeed("148070", "KIWOOM 국고채10년", PriceProvider.TOSS),
            new EtfSeed("261240", "KODEX 미국달러선물", PriceProvider.TOSS),
            new EtfSeed("381170", "TIGER 미국테크TOP10 INDXX", PriceProvider.TOSS),
            new EtfSeed("381180", "TIGER 미국필라델피아반도체나스닥", PriceProvider.TOSS),
            new EtfSeed("203780", "TIGER 미국나스닥바이오", PriceProvider.TOSS),
            new EtfSeed("390400", "KODEX 미국스마트모빌리티S&P", PriceProvider.TOSS),
            new EtfSeed("218420", "KODEX 미국S&P500에너지(합성)", PriceProvider.TOSS),
            new EtfSeed("453650", "KODEX 미국S&P500금융", PriceProvider.TOSS),
            new EtfSeed("494840", "TIGER 미국방산TOP10", PriceProvider.TOSS),
            new EtfSeed("BTC", "비트코인", PriceProvider.COINGECKO));

    /** §4.1~4.3의 11개 섹터와 코인 섹터 기준가 정책에 따라 추가된 `CRYPTO`(BTC) 1건. */
    public static final List<SectorSeed> SECTORS = List.of(
            new SectorSeed("DEPOSIT", "BASE_ASSET", "459580", "예금", "국내 단기금리 기반", 1),
            new SectorSeed("GOLD", "BASE_ASSET", "132030", "금", "글로벌 금 선물 기반", 2),
            new SectorSeed("BOND", "BASE_ASSET", "148070", "채권", "국내 국고채 기반", 3),
            new SectorSeed("USD", "BASE_ASSET", "261240", "달러", "달러/원 환율 기반", 4),
            new SectorSeed("TECH", "FUTURE_TECH", "381170", "테크", "미국 빅테크 중심", 1),
            new SectorSeed("SEMICONDUCTOR", "FUTURE_TECH", "381180", "반도체", "미국 반도체 산업 중심", 2),
            new SectorSeed("BIO", "FUTURE_TECH", "203780", "바이오/제약", "미국 바이오·제약 산업 중심", 3),
            new SectorSeed("CRYPTO", "FUTURE_TECH", "BTC", "코인", "비트코인(BTC) 시세 기반, 매일 09:00 KST 기준가", 4),
            new SectorSeed("AUTO", "REAL_ECONOMY", "390400", "자동차", "미국 자동차·모빌리티 산업 중심", 1),
            new SectorSeed("ENERGY", "REAL_ECONOMY", "218420", "에너지", "미국 에너지 산업 중심", 2),
            new SectorSeed("FINANCE", "REAL_ECONOMY", "453650", "금융", "미국 금융 산업 중심", 3),
            new SectorSeed("DEFENSE", "REAL_ECONOMY", "494840", "방산", "미국 방산 산업 중심", 4));
}
