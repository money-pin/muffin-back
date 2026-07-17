package com.muffin.sector.domain.etf;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ETF 애그리거트 루트. 섹터가 ID로 참조하는 투자 상품 정보를 가진다. */
@Getter
@Entity
@Table(name = "etf", uniqueConstraints = @UniqueConstraint(name = "uk_etf_etf_code", columnNames = "etf_code"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Etf extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "etf_id")
    private Long id;

    @Column(name = "etf_code", nullable = false, length = 20)
    private String etfCode;

    @Column(name = "etf_name", nullable = false, length = 50)
    private String etfName;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_provider", nullable = false, length = 20)
    private PriceProvider priceProvider;

    private Etf(String etfCode, String etfName, PriceProvider priceProvider) {
        this.etfCode = etfCode;
        this.etfName = etfName;
        this.priceProvider = priceProvider;
    }

    /** 토스증권 시세로 수집하는 ETF 레코드를 생성한다. */
    public static Etf create(String etfCode, String etfName) {
        return new Etf(etfCode, etfName, PriceProvider.TOSS);
    }

    /** 토스증권 외 다른 데이터 제공처(예: CoinGecko)로 시세를 수집하는 레코드를 생성한다. */
    public static Etf create(String etfCode, String etfName, PriceProvider priceProvider) {
        return new Etf(etfCode, etfName, priceProvider);
    }
}
