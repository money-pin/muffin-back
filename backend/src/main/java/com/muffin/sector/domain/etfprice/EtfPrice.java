package com.muffin.sector.domain.etfprice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** ETF 일별 시세 애그리거트 루트. 가격과 수집 상태를 함께 저장하며, 다른 애그리거트(Etf)는 ID로만 참조한다. */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "end_price_status", nullable = false)
    private PriceCollectionStatus endPriceStatus;

    @Column(name = "start_price")
    private Long startPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_price_status", nullable = false)
    private PriceCollectionStatus startPriceStatus;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    private EtfPrice(Long etfId, LocalDate priceDate, Long startPrice, Long endPrice) {
        this.etfId = etfId;
        this.priceDate = priceDate;
        if (startPrice == null) {
            this.startPriceStatus = PriceCollectionStatus.PENDING;
        } else {
            this.startPrice = requirePositive(startPrice, "startPrice");
            this.startPriceStatus = PriceCollectionStatus.SUCCESS;
        }
        if (endPrice == null) {
            this.endPriceStatus = PriceCollectionStatus.PENDING;
        } else {
            this.endPrice = requirePositive(endPrice, "endPrice");
            this.endPriceStatus = PriceCollectionStatus.SUCCESS;
        }
    }

    /** 시가만 확보된 레코드를 생성한다. 종가는 이후 {@link #recordClose(Long)}로 채운다. */
    public static EtfPrice open(Long etfId, LocalDate priceDate, Long startPrice) {
        return new EtfPrice(etfId, priceDate, startPrice, null);
    }

    /** 시가와 종가가 모두 확보된 레코드를 생성한다. */
    public static EtfPrice create(Long etfId, LocalDate priceDate, Long startPrice, Long endPrice) {
        return new EtfPrice(etfId, priceDate, startPrice, endPrice);
    }

    /** 아직 시가와 종가를 수집하지 않은 날짜별 상태 행을 생성한다. */
    public static EtfPrice pending(Long etfId, LocalDate priceDate) {
        return new EtfPrice(etfId, priceDate, null, null);
    }

    /** 시가를 반영한다. 같은 값을 여러 번 반영해도 결과는 같다. */
    public void recordOpen(Long startPrice) {
        if (startPriceStatus == PriceCollectionStatus.FINAL_MISSING) {
            return;
        }
        this.startPrice = requirePositive(startPrice, "startPrice");
        this.startPriceStatus = PriceCollectionStatus.SUCCESS;
    }

    /** 종가를 반영한다. 같은 값을 여러 번 반영해도 결과는 같다. */
    public void recordClose(Long endPrice) {
        this.endPrice = requirePositive(endPrice, "endPrice");
        this.endPriceStatus = PriceCollectionStatus.SUCCESS;
    }

    public void markOpenNoData() {
        markOpenUnavailable(PriceCollectionStatus.NO_DATA);
    }

    public void markOpenFailed() {
        markOpenUnavailable(PriceCollectionStatus.FAILED);
    }

    public void markOpenFinalMissing() {
        markOpenUnavailable(PriceCollectionStatus.FINAL_MISSING);
    }

    public void markOpenMarketClosed() {
        markOpenUnavailable(PriceCollectionStatus.MARKET_CLOSED);
    }

    public void markCloseNoData() {
        markCloseUnavailable(PriceCollectionStatus.NO_DATA);
    }

    public void markCloseFailed() {
        markCloseUnavailable(PriceCollectionStatus.FAILED);
    }

    public void markCloseFinalMissing() {
        if (endPriceStatus == PriceCollectionStatus.MARKET_CLOSED) {
            return;
        }
        markCloseUnavailable(PriceCollectionStatus.FINAL_MISSING);
    }

    public void markCloseMarketClosed() {
        markCloseUnavailable(PriceCollectionStatus.MARKET_CLOSED);
    }

    private void markOpenUnavailable(PriceCollectionStatus status) {
        if (startPriceStatus == PriceCollectionStatus.SUCCESS
                || startPriceStatus == PriceCollectionStatus.FINAL_MISSING) {
            return;
        }
        this.startPrice = null;
        this.startPriceStatus = status;
    }

    private void markCloseUnavailable(PriceCollectionStatus status) {
        if (endPriceStatus == PriceCollectionStatus.SUCCESS) {
            return;
        }
        this.endPrice = null;
        this.endPriceStatus = status;
    }

    private static Long requirePositive(Long price, String fieldName) {
        Objects.requireNonNull(price, fieldName + "는 null일 수 없습니다.");
        if (price <= 0) {
            throw new IllegalArgumentException(fieldName + "는 0보다 커야 합니다.");
        }
        return price;
    }
}
