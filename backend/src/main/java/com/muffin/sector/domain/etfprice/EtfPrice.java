package com.muffin.sector.domain.etfprice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ETF 일별 시세 애그리거트 루트. 배치가 매일 적재하는 스냅샷이며, 다른 애그리거트(Etf)는 ID로만 참조한다. */
@Getter
@Entity
@Table(name = "etf_price")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EtfPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "etf_prices_id")
    private Long id;

    @Column(name = "etf_id", nullable = false)
    private Long etfId;

    @Column(name = "end_price", nullable = false)
    private Long endPrice;

    @Column(name = "start_price", nullable = false)
    private Long startPrice;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "is_fallback", nullable = false)
    private boolean isFallback;

    private EtfPrice(Long etfId, LocalDate priceDate, Long startPrice, Long endPrice, boolean isFallback) {
        this.etfId = etfId;
        this.priceDate = priceDate;
        this.startPrice = startPrice;
        this.endPrice = endPrice;
        this.isFallback = isFallback;
    }

    /** 정상 시세 레코드를 생성한다. */
    public static EtfPrice create(Long etfId, LocalDate priceDate, Long startPrice, Long endPrice) {
        return new EtfPrice(etfId, priceDate, startPrice, endPrice, false);
    }

    /** 시세를 조회하지 못했을 때 0원으로 대체하는 폴백 레코드를 생성한다. */
    public static EtfPrice fallback(Long etfId, LocalDate priceDate) {
        return new EtfPrice(etfId, priceDate, 0L, 0L, true);
    }
}
