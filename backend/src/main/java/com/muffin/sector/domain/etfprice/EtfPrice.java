package com.muffin.sector.domain.etfprice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ETF 일별 시세 애그리거트 루트. 배치가 매일 적재하는 스냅샷이며, 다른 애그리거트(Etf)는 ID로만 참조한다. */
@Getter
@Entity
@Table(
        name = "etf_price",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_etf_price_etf_price_date",
                        columnNames = {"etf_id", "price_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EtfPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "etf_prices_id")
    private Long id;

    @Column(name = "etf_id", nullable = false)
    private Long etfId;

    @Column(name = "end_price")
    private Long endPrice;

    @Column(name = "start_price")
    private Long startPrice;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    private EtfPrice(Long etfId, LocalDate priceDate, Long startPrice, Long endPrice) {
        this.etfId = etfId;
        this.priceDate = priceDate;
        this.startPrice = startPrice;
        this.endPrice = endPrice;
    }

    /** 시가만 확보된 레코드를 생성한다. 종가는 이후 {@link #recordClose(Long)}로 채운다. */
    public static EtfPrice open(Long etfId, LocalDate priceDate, Long startPrice) {
        return new EtfPrice(etfId, priceDate, startPrice, null);
    }

    /** 시가와 종가가 모두 확보된 레코드를 생성한다. */
    public static EtfPrice create(Long etfId, LocalDate priceDate, Long startPrice, Long endPrice) {
        return new EtfPrice(etfId, priceDate, startPrice, endPrice);
    }

    /** 시가를 반영한다. 같은 값을 여러 번 반영해도 결과는 같다. */
    public void recordOpen(Long startPrice) {
        this.startPrice = Objects.requireNonNull(startPrice, "startPrice는 null일 수 없습니다.");
    }

    /** 종가를 반영한다. 같은 값을 여러 번 반영해도 결과는 같다. */
    public void recordClose(Long endPrice) {
        this.endPrice = Objects.requireNonNull(endPrice, "endPrice는 null일 수 없습니다.");
    }
}
