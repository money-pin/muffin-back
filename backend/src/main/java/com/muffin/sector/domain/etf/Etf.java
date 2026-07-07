package com.muffin.sector.domain.etf;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(
        name = "etf",
        uniqueConstraints = {@UniqueConstraint(name = "uk_etf_etf_code", columnNames = "etf_code")})
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

    private Etf(String etfCode, String etfName) {
        this.etfCode = etfCode;
        this.etfName = etfName;
    }

    /** ETF 레코드를 생성한다. */
    public static Etf create(String etfCode, String etfName) {
        return new Etf(etfCode, etfName);
    }
}
